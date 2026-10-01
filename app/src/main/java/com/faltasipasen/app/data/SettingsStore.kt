package com.faltasipasen.app.data

import android.content.SharedPreferences
import com.faltasipasen.app.domain.CourseDates
import com.faltasipasen.app.domain.Credentials
import com.faltasipasen.app.domain.EducationLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

interface SettingsStore {
    /** Se incrementa en cada escritura; la UI lo observa para refrescarse. */
    val changes: StateFlow<Long>
    fun credentials(): Credentials?
    fun saveCredentials(credentials: Credentials)
    fun clearAll()
    fun thresholdPercent(): Double
    fun setThresholdPercent(value: Double)
    fun cutoffDate(): LocalDate
    fun setCutoffDate(value: LocalDate)
    fun syncIntervalHours(): Int
    fun setSyncIntervalHours(value: Int)
    fun activeAlertKeys(): Set<String>
    fun setActiveAlertKeys(keys: Set<String>)
    fun lastError(): String?
    fun setLastError(message: String?)
    /** iPasen rechazó estas credenciales: no se reintenta hasta que se guarden otras. */
    fun credentialsRejected(): Boolean
    fun setCredentialsRejected(value: Boolean)
    fun themeMode(): ThemeMode
    fun setThemeMode(value: ThemeMode)
    fun educationLevel(): EducationLevel
    fun setEducationLevel(value: EducationLevel)
}

class PrefsSettingsStore(
    private val prefs: SharedPreferences,
    private val cipher: StringCipher,
    private val today: () -> LocalDate,
) : SettingsStore {
    private val version = MutableStateFlow(0L)
    override val changes: StateFlow<Long> = version.asStateFlow()

    override fun credentials(): Credentials? {
        val user = prefs.getString(KEY_USER, null)?.let(cipher::decrypt) ?: return null
        val pass = prefs.getString(KEY_PASS, null)?.let(cipher::decrypt) ?: return null
        return Credentials(user, pass)
    }

    override fun saveCredentials(credentials: Credentials) = write {
        putString(KEY_USER, cipher.encrypt(credentials.username))
        putString(KEY_PASS, cipher.encrypt(credentials.password))
        remove(KEY_ACTIVE_ALERTS)
        remove(KEY_LAST_ERROR)
        remove(KEY_CREDENTIALS_REJECTED)
    }

    override fun clearAll() = write { clear() }

    override fun thresholdPercent(): Double =
        prefs.getString(KEY_THRESHOLD, null)?.toDoubleOrNull() ?: educationLevel().defaultThreshold

    override fun setThresholdPercent(value: Double) = write { putString(KEY_THRESHOLD, value.toString()) }

    override fun cutoffDate(): LocalDate =
        prefs.getString(KEY_CUTOFF, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: CourseDates.defaultCutoff(today(), educationLevel())

    override fun setCutoffDate(value: LocalDate) = write { putString(KEY_CUTOFF, value.toString()) }

    override fun syncIntervalHours(): Int = prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL_HOURS)

    override fun setSyncIntervalHours(value: Int) = write { putInt(KEY_INTERVAL, value) }

    override fun activeAlertKeys(): Set<String> = prefs.getStringSet(KEY_ACTIVE_ALERTS, null)?.toSet() ?: emptySet()

    override fun setActiveAlertKeys(keys: Set<String>) = write { putStringSet(KEY_ACTIVE_ALERTS, keys.toSet()) }

    override fun lastError(): String? = prefs.getString(KEY_LAST_ERROR, null)

    override fun setLastError(message: String?) = write {
        if (message == null) remove(KEY_LAST_ERROR) else putString(KEY_LAST_ERROR, message)
    }

    override fun credentialsRejected(): Boolean = prefs.getBoolean(KEY_CREDENTIALS_REJECTED, false)

    override fun setCredentialsRejected(value: Boolean) = write { putBoolean(KEY_CREDENTIALS_REJECTED, value) }

    override fun themeMode(): ThemeMode =
        prefs.getString(KEY_THEME, null)?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.SYSTEM

    override fun setThemeMode(value: ThemeMode) = write { putString(KEY_THEME, value.name) }

    override fun educationLevel(): EducationLevel =
        prefs.getString(KEY_EDUCATION_LEVEL, null)
            ?.let { stored -> EducationLevel.entries.firstOrNull { it.name == stored } }
            ?: EducationLevel.FP_SUPERIOR

    override fun setEducationLevel(value: EducationLevel) = write { putString(KEY_EDUCATION_LEVEL, value.name) }

    private fun write(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        version.update { it + 1 }
    }

    companion object {
        const val DEFAULT_INTERVAL_HOURS = 12
        private const val KEY_USER = "usuario_cifrado"
        private const val KEY_PASS = "clave_cifrada"
        private const val KEY_THRESHOLD = "umbral"
        private const val KEY_CUTOFF = "fecha_corte"
        private const val KEY_INTERVAL = "intervalo_horas"
        private const val KEY_ACTIVE_ALERTS = "avisos_activos"
        private const val KEY_LAST_ERROR = "ultimo_error"
        private const val KEY_THEME = "tema"
        private const val KEY_EDUCATION_LEVEL = "etapa_educativa"
        private const val KEY_CREDENTIALS_REJECTED = "credenciales_rechazadas"
    }
}
