package com.faltasipasen.app.data

import com.faltasipasen.app.domain.Credentials
import com.faltasipasen.app.testutil.InMemorySharedPreferences
import com.faltasipasen.app.testutil.ReversingCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrefsSettingsStoreTest {
    private val prefs = InMemorySharedPreferences()
    private val store = PrefsSettingsStore(prefs, ReversingCipher()) { LocalDate.of(2026, 9, 28) }

    @Test
    fun `no credentials by default`() {
        assertNull(store.credentials())
    }

    @Test
    fun `credentials round trip and are never stored in plain text`() {
        store.saveCredentials(Credentials("alumno", "s3cret"))
        assertEquals(Credentials("alumno", "s3cret"), store.credentials())
        assertFalse(prefs.values.values.any { it == "alumno" || it == "s3cret" })
    }

    @Test
    fun `undecryptable credentials count as missing`() {
        store.saveCredentials(Credentials("alumno", "s3cret"))
        prefs.values.keys.filter { (prefs.values[it] as? String)?.startsWith("enc:") == true }
            .forEach { prefs.values[it] = "basura" }
        assertNull(store.credentials())
    }

    @Test
    fun `defaults need no user input`() {
        assertEquals(15.0, store.thresholdPercent(), 0.0)
        assertEquals(LocalDate.of(2027, 2, 15), store.cutoffDate())
        assertEquals(12, store.syncIntervalHours())
        assertEquals(emptySet<String>(), store.activeAlertKeys())
        assertNull(store.lastError())
    }

    @Test
    fun `values are persisted`() {
        store.setThresholdPercent(20.5)
        store.setCutoffDate(LocalDate.of(2027, 2, 12))
        store.setSyncIntervalHours(6)
        store.setActiveAlertKeys(setOf("__total__"))
        store.setLastError("fallo")
        val reopened = PrefsSettingsStore(prefs, ReversingCipher()) { LocalDate.of(2026, 9, 28) }
        assertEquals(20.5, reopened.thresholdPercent(), 0.0)
        assertEquals(LocalDate.of(2027, 2, 12), reopened.cutoffDate())
        assertEquals(6, reopened.syncIntervalHours())
        assertEquals(setOf("__total__"), reopened.activeAlertKeys())
        assertEquals("fallo", reopened.lastError())
    }

    @Test
    fun `saving new credentials resets alert state and last error`() {
        store.setActiveAlertKeys(setOf("__total__"))
        store.setLastError("clave incorrecta")
        store.saveCredentials(Credentials("alumno", "nueva"))
        assertEquals(emptySet<String>(), store.activeAlertKeys())
        assertNull(store.lastError())
    }

    @Test
    fun `setting last error to null removes it`() {
        store.setLastError("fallo")
        store.setLastError(null)
        assertNull(store.lastError())
    }

    @Test
    fun `every write bumps the change counter`() {
        val before = store.changes.value
        store.setThresholdPercent(10.0)
        store.setLastError(null)
        assertEquals(before + 2, store.changes.value)
    }

    @Test
    fun `clearAll wipes everything`() {
        store.saveCredentials(Credentials("alumno", "s3cret"))
        store.setThresholdPercent(30.0)
        store.clearAll()
        assertNull(store.credentials())
        assertEquals(15.0, store.thresholdPercent(), 0.0)
        assertTrue(prefs.values.isEmpty())
    }

    @Test
    fun `theme defaults to system and is persisted`() {
        assertEquals(ThemeMode.SYSTEM, store.themeMode())
        store.setThemeMode(ThemeMode.AMOLED)
        assertEquals(ThemeMode.AMOLED, PrefsSettingsStore(prefs, ReversingCipher()) { LocalDate.of(2026, 9, 28) }.themeMode())
    }

    @Test
    fun `unknown stored theme falls back to system`() {
        prefs.values["tema"] = "SEPIA"
        assertEquals(ThemeMode.SYSTEM, store.themeMode())
    }
}
