package com.faltasipasen.app.scraper

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.faltasipasen.app.domain.Credentials
import com.faltasipasen.app.scraper.ScrapeException.Reason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

class WebViewScraper(private val context: Context) : IpasenScraper {

    override suspend fun scrape(credentials: Credentials): ScrapeResult {
        if (IpasenSite.LOGIN_URL.isBlank()) {
            throw ScrapeException(Reason.PARSE_ERROR, "scraper de iPasen sin configurar")
        }
        return withContext(Dispatchers.Main) {
            val session = Session(context.applicationContext)
            try {
                withTimeout(TOTAL_TIMEOUT_MS) { session.run(credentials) }
            } catch (e: TimeoutCancellationException) {
                throw ScrapeException(Reason.TIMEOUT, "la sincronización completa superó el tiempo máximo")
            } finally {
                session.destroy()
            }
        }
    }

    private sealed interface PageEvent {
        data class Finished(val url: String) : PageEvent
        data class Failed(val description: String) : PageEvent
    }

    private class Bridge(private val results: Channel<Pair<String, String>>) {
        @JavascriptInterface
        fun onResult(step: String, json: String) {
            results.trySend(step to json)
        }
    }

    private class Session(private val context: Context) {
        private val pageEvents = Channel<PageEvent>(Channel.UNLIMITED)
        private val results = Channel<Pair<String, String>>(Channel.UNLIMITED)
        private val webView: WebView = createWebView()

        @SuppressLint("SetJavaScriptEnabled")
        private fun createWebView(): WebView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            addJavascriptInterface(Bridge(results), BRIDGE_NAME)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val host = request.url.host ?: return true
                    return IpasenSite.ALLOWED_HOST_SUFFIXES.none { host == it || host.endsWith(".$it") }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    pageEvents.trySend(PageEvent.Finished(url))
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) pageEvents.trySend(PageEvent.Failed(error.description.toString()))
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
            load(IpasenSite.LOGIN_URL)
            drainPageEvents()
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
                is PageEvent.Failed -> throw ScrapeException(Reason.NETWORK, event.description)
                is PageEvent.Finished -> Unit
                null -> if (required) throw ScrapeException(Reason.TIMEOUT, "la página de iPasen no terminó de cargar")
            }
        }

        private suspend fun runStep(step: String, asset: String, prelude: String = ""): String {
            while (results.tryReceive().isSuccess) Unit
            val script = buildScript(step, asset, prelude)
            webView.evaluateJavascript(script, null)
            var reinjections = 0
            val deadline = System.currentTimeMillis() + STEP_TIMEOUT_MS
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) throw ScrapeException(Reason.TIMEOUT, "el paso '$step' no respondió")
                val result = withTimeoutOrNull(minOf(remaining, POLL_MS)) { results.receive() }
                if (result != null && result.first == step) return result.second

                when (val event = pageEvents.tryReceive().getOrNull()) {
                    is PageEvent.Failed -> throw ScrapeException(Reason.NETWORK, event.description)
                    is PageEvent.Finished -> if (step != ScrapeStep.LOGIN && reinjections < MAX_REINJECTIONS) {
                        reinjections++
                        webView.evaluateJavascript(script, null)
                    }
                    null -> Unit
                }
            }
        }

        private fun buildScript(step: String, asset: String, prelude: String): String = buildString {
            append("(function(){\n")
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
        const val BRIDGE_NAME = "AndroidBridge"
        const val COMMON_ASSET = "scraper/common.js"
        const val LOGIN_ASSET = "scraper/login.js"
        const val LOGIN_CHECK_ASSET = "scraper/login_check.js"
        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 2340
        const val PAGE_TIMEOUT_MS = 30_000L
        const val LOGIN_NAVIGATION_TIMEOUT_MS = 15_000L
        const val STEP_TIMEOUT_MS = 45_000L
        const val TOTAL_TIMEOUT_MS = 240_000L
        const val POLL_MS = 500L
        const val MAX_REINJECTIONS = 3
    }
}
