package com.faltasipasen.app.data

import com.faltasipasen.app.domain.Absence
import com.faltasipasen.app.domain.CalendarDay
import com.faltasipasen.app.domain.ScheduleSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Snapshot(
    val schedule: List<ScheduleSlot>,
    val calendar: List<CalendarDay>,
    val absences: List<Absence>,
    val syncedAtMillis: Long,
)

interface SnapshotStore {
    val current: StateFlow<Snapshot?>
    fun save(snapshot: Snapshot)
    fun clear()
}

class FileSnapshotStore(private val file: File) : SnapshotStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(readFromDisk())
    override val current: StateFlow<Snapshot?> = state.asStateFlow()

    @Synchronized
    override fun save(snapshot: Snapshot) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(Snapshot.serializer(), snapshot))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "No se pudo guardar ${file.name}" }
        }
        state.value = snapshot
    }

    @Synchronized
    override fun clear() {
        file.delete()
        state.value = null
    }

    private fun readFromDisk(): Snapshot? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(Snapshot.serializer(), file.readText()) }.getOrNull()
    }
}
