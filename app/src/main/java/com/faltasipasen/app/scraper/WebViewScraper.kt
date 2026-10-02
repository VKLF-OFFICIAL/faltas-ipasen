package com.faltasipasen.app.scraper

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.faltasipasen.app.domain.Credentials
import com.faltasipasen.app.scraper.ScrapeException.Reason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID
import kotlin.coroutines.resume

class WebViewScraper(private val context: Context) : IpasenScraper {

    override suspend fun scrape(credentials: Credentials): ScrapeResult {
        if (IpasenSite.LOGIN_URL.isBlank()) {
            throw ScrapeException(Reason.PARSE_ERROR, "scraper de iPasen sin configurar")
        }
        return withContext(Dispatchers.Main) {
            // Falla si el WebView del sistema no está disponible (p. ej. mientras se actualiza).
            val session = try {
                Session(context.applicationContext)
            } catch (e: Exception) {
                throw ScrapeException(Reason.INTERNAL, "no se pudo abrir el navegador interno (${describe(e)})")
            }
            try {
                withTimeout(TOTAL_TIMEOUT_MS) { session.run(credentials) }
            } catch (e: TimeoutCancellationException) {
                throw ScrapeException(Reason.TIMEOUT, "la sincronización superó el tiempo máximo en el paso '${session.currentStep}'")
            } catch (e: ScrapeException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw ScrapeException(Reason.INTERNAL, "fallo en el navegador interno (${describe(e)})")
            } finally {
                session.destroy()
            }
        }
    }

    private sealed interface PageEvent {
        data class Finished(val url: String) : PageEvent
        data class Failed(val description: String, val reason: Reason = Reason.NETWORK) : PageEvent
    }

    /** Solo acepta resultados con el [token] de la sesión: el puente es visible para cualquier iframe. */
    private class Bridge(private val results: Channel<Pair<String, String>>, private val token: String) {
        @JavascriptInterface
        fun onResult(token: String, step: String, json: String) {
            if (token == this.token) results.trySend(step to json)
        }
    }

    private class Session(private val context: Context) {
        private val token = UUID.randomUUID().toString()
        private val pageEvents = Channel<PageEvent>(Channel.UNLIMITED)
        private val results = Channel<Pair<String, String>>(Channel.UNLIMITED)
        private val webView: WebView = createWebView()

        /** Paso en curso, para que el timeout global diga dónde se quedó. */
        var currentStep = "inicio"
            private set

        @SuppressLint("SetJavaScriptEnabled")
        private fun createWebView(): WebView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            addJavascriptInterface(Bridge(results, token), BRIDGE_NAME)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val host = request.url.host
                    val allowed = host != null && request.url.scheme == "https" &&
                        IpasenSite.ALLOWED_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }
                    // Sin este aviso, una redirección bloqueada solo se notaría como un timeout genérico.
                    if (!allowed && request.isForMainFrame) {
                        pageEvents.trySend(
                            PageEvent.Failed("Séneca intentó abrir una web no permitida (${host ?: request.url.scheme})", Reason.PARSE_ERROR),
                        )
                    }
                    return !allowed
                }

                override fun onPageFinished(view: WebView, url: String) {
                    pageEvents.trySend(PageEvent.Finished(url))
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) pageEvents.trySend(PageEvent.Failed(error.description.toString()))
                }

                // Séneca caído o en mantenimiento: la página "carga" pero es un error del servidor.
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame && response.statusCode >= 500) {
                        pageEvents.trySend(PageEvent.Failed("Séneca respondió con un error ${response.statusCode}"))
                    }
                }
            }
            // Sin ventana: tamaño de móvil para que la web maquete y ejecute con normalidad.
            measure(
                View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)
            resumeTimers()
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }

        suspend fun run(credentials: Credentials): ScrapeResult {
            clearCookies()
            currentStep = "carga del acceso"
            load(IpasenSite.LOGIN_URL)
            drainPageEvents()
            verifyLoginPage()
            ScrapeParsers.checkOk(runStep(ScrapeStep.LOGIN, LOGIN_ASSET, credentialsJs(credentials)), "login")
            awaitPage(LOGIN_NAVIGATION_TIMEOUT_MS, required = false)
            ScrapeParsers.checkOk(runStep(ScrapeStep.LOGIN_CHECK, LOGIN_CHECK_ASSET), "login")

            val schedule = ScrapeParsers.parseSchedule(runDataStep(IpasenSite.SCHEDULE))
            val calendar = ScrapeParsers.parseCalendar(runDataStep(IpasenSite.CALENDAR))
            val absences = ScrapeParsers.parseAbsences(runDataStep(IpasenSite.ABSENCES))
            return ScrapeResult(schedule, calendar, absences)
        }

        fun destroy() {
            webView.stopLoading()
            webView.removeJavascriptInterface(BRIDGE_NAME)
            webView.destroy()
            CookieManager.getInstance().removeAllCookies(null)
        }

        /**
         * Las credenciales solo se envían si la página cargada es de verdad la de acceso de
         * Séneca. Evita inyectarlas en una redirección o una página distinta.
         */
        private fun verifyLoginPage() {
            val current = webView.url?.let(Uri::parse)
            val expectedHost = Uri.parse(IpasenSite.LOGIN_URL).host
            if (current?.scheme != "https" || current.host != expectedHost) {
                throw ScrapeException(
                    Reason.PARSE_ERROR,
                    "la página de acceso no es la de Séneca; no se envían las credenciales",
                )
            }
        }

        private suspend fun runDataStep(step: ScrapeStep): String {
            step.url?.let { load(it) }
            return runStep(step.id, step.asset)
        }

        private suspend fun load(url: String) {
            drainPageEvents()
            webView.loadUrl(url)
            awaitPage(PAGE_TIMEOUT_MS, required = true)
        }

        private suspend fun awaitPage(timeoutMs: Long, required: Boolean) {
            when (val event = withTimeoutOrNull(timeoutMs) { pageEvents.receive() }) {
                is PageEvent.Failed -> throw ScrapeException(event.reason, event.description)
                is PageEvent.Finished -> Unit
                null -> if (required) throw ScrapeException(Reason.TIMEOUT, "la página de iPasen no terminó de cargar")
            }
        }

        private suspend fun runStep(step: String, asset: String, prelude: String = ""): String {
            currentStep = step
            while (results.tryReceive().isSuccess) Unit
            val script = buildScript(step, asset, prelude)
            webView.evaluateJavascript(script, null)
            var reinjections = 0
            // Reloj monotónico: un cambio de hora del móvil no debe adelantar ni alargar el plazo.
            val deadline = SystemClock.elapsedRealtime() + STEP_TIMEOUT_MS
            while (true) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) throw ScrapeException(Reason.TIMEOUT, "el paso '$step' no respondió [${describePage()}]")
                val result = withTimeoutOrNull(minOf(remaining, POLL_MS)) { results.receive() }
                if (result != null && result.first == step) return result.second

                when (val event = pageEvents.tryReceive().getOrNull()) {
                    is PageEvent.Failed -> throw ScrapeException(event.reason, event.description)
                    is PageEvent.Finished -> if (step != ScrapeStep.LOGIN && reinjections < MAX_REINJECTIONS) {
                        reinjections++
                        webView.evaluateJavascript(script, null)
                    }
                    null -> Unit
                }
            }
        }

        /** Resumen de la página actual para el mensaje de error; sin él un timeout no dice dónde se atascó. */
        private suspend fun describePage(): String {
            val script = buildString {
                append("(function(){\nconst STEP='diagnostico';\n")
                append(readAsset(COMMON_ASSET)).append('\n')
                append("return describePage();\n})();")
            }
            val raw = withTimeoutOrNull(DESCRIBE_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    webView.evaluateJavascript(script) { if (cont.isActive) cont.resume(it) }
                }
            }
            val text = raw?.let { runCatching { JSONTokener(it).nextValue() as? String }.getOrNull() }
            return text?.takeIf { it.isNotBlank() } ?: "la página no responde"
        }

        private fun buildScript(step: String, asset: String, prelude: String): String = buildString {
            append("(function(){\n")
            append("const TOKEN=").append(JSONObject.quote(token)).append(";\n")
            append("const STEP=").append(JSONObject.quote(step)).append(";\n")
            append(prelude).append('\n')
            append(readAsset(COMMON_ASSET)).append('\n')
            append(readAsset(asset)).append('\n')
            append("})();")
        }

        private fun credentialsJs(credentials: Credentials): String =
            "const CREDS={user:${JSONObject.quote(credentials.username)},pass:${JSONObject.quote(credentials.password)}};"

        private fun readAsset(path: String): String =
            context.assets.open(path).bufferedReader().use { it.readText() }

        private fun drainPageEvents() {
            while (pageEvents.tryReceive().isSuccess) Unit
        }

        private suspend fun clearCookies() = suspendCancellableCoroutine { cont ->
            CookieManager.getInstance().removeAllCookies { if (cont.isActive) cont.resume(Unit) }
        }
    }

    private companion object {
        fun describe(e: Exception): String = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName

        const val BRIDGE_NAME = "AndroidBridge"
        const val COMMON_ASSET = "scraper/common.js"
        const val LOGIN_ASSET = "scraper/login.js"
        const val LOGIN_CHECK_ASSET = "scraper/login_check.js"
        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 2340
        const val PAGE_TIMEOUT_MS = 45_000L
        const val LOGIN_NAVIGATION_TIMEOUT_MS = 30_000L
        const val STEP_TIMEOUT_MS = 100_000L
        const val TOTAL_TIMEOUT_MS = 420_000L
        const val POLL_MS = 500L
        const val MAX_REINJECTIONS = 3
        const val DESCRIBE_TIMEOUT_MS = 3_000L
    }
}
