package com.faltasipasen.app.data

import com.faltasipasen.app.domain.Absence
import com.faltasipasen.app.domain.CalendarDay
import com.faltasipasen.app.domain.ScheduleSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class FileSnapshotStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val snapshot = Snapshot(
        schedule = listOf(ScheduleSlot("REDES", 1, 1.0)),
        calendar = listOf(CalendarDay(LocalDate.of(2026, 9, 14), true)),
        absences = listOf(Absence(LocalDate.of(2026, 9, 14), "REDES", 1.0, false)),
        syncedAtMillis = 1_790_000_000_000,
    )

    private fun file() = File(tmp.root, "snapshot.json")

    @Test
    fun `empty store has no snapshot`() {
        assertNull(FileSnapshotStore(file()).current.value)
    }

    @Test
    fun `saved snapshot is visible immediately and after reopening`() {
        val store = FileSnapshotStore(file())
        store.save(snapshot)
        assertEquals(snapshot, store.current.value)
        assertEquals(snapshot, FileSnapshotStore(file()).current.value)
    }

    @Test
    fun `saving twice replaces the previous snapshot`() {
        val store = FileSnapshotStore(file())
        store.save(snapshot)
        val newer = snapshot.copy(absences = emptyList(), syncedAtMillis = 1_790_000_100_000)
        store.save(newer)
        assertEquals(newer, FileSnapshotStore(file()).current.value)
    }

    @Test
    fun `corrupted file is treated as no snapshot`() {
        file().writeText("{not json")
        assertNull(FileSnapshotStore(file()).current.value)
    }

    @Test
    fun `clear deletes the file and the current value`() {
        val store = FileSnapshotStore(file())
        store.save(snapshot)
        store.clear()
        assertNull(store.current.value)
        assertFalse(file().exists())
    }

    @Test
    fun `clear also removes a leftover temporary file from an interrupted save`() {
        val store = FileSnapshotStore(file())
        store.save(snapshot)
        val leftover = File(tmp.root, "snapshot.json.tmp").apply { writeText("{}") }
        store.clear()
        assertFalse(leftover.exists())
    }

    @Test
    fun `snapshot saved before the new fields existed still loads`() {
        file().writeText(
            """{"schedule":[{"subject":"REDES","dayOfWeek":1,"hours":1.0}],""" +
                """"calendar":[{"date":"2026-09-14","lectivo":true}],""" +
                """"absences":[{"date":"2026-09-14","subject":"REDES","hours":1.0,"justified":false}],""" +
                """"syncedAtMillis":1}""",
        )
        val loaded = FileSnapshotStore(file()).current.value!!
        assertEquals(ScheduleSlot("REDES", 1, 1.0), loaded.schedule.single())
        assertEquals(Absence(LocalDate.of(2026, 9, 14), "REDES", 1.0, false), loaded.absences.single())
    }
}
