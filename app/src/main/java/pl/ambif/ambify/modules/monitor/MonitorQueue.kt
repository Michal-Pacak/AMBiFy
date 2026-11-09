package pl.ambif.ambify.modules.monitor

import android.content.Context
import java.io.File
import kotlinx.serialization.json.Json
import pl.ambif.ambify.shared.MonitorEvent

/**
 * Lokalna kolejka zdarzeń czekających na wysłanie na serwer (JSONL w `filesDir`). Offline rośnie, po udanej
 * wysyłce najstarsze są usuwane. Tylko build „kids”. Trzyma logikę plikową z dala od Androida, żeby dała się
 * przetestować na JVM (konstruktor bierze [file]; [of] podpina `filesDir`).
 */
class MonitorQueue(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Dopisuje zdarzenia; przycina do [MAX] najnowszych, żeby plik nie rósł bez końca. */
    @Synchronized
    fun add(events: List<MonitorEvent>) {
        if (events.isEmpty()) return
        file.parentFile?.mkdirs()
        val text = events.joinToString(separator = "\n", postfix = "\n") { json.encodeToString(MonitorEvent.serializer(), it) }
        runCatching {
            file.appendText(text)
            val lines = file.readLines().filter { it.isNotBlank() }
            if (lines.size > MAX) file.writeText(lines.takeLast(MAX).joinToString(separator = "\n", postfix = "\n"))
        }
    }

    /** Wszystkie zdarzenia w kolejce (do wysyłki); nie usuwa. */
    @Synchronized
    fun peekAll(): List<MonitorEvent> =
        if (!file.exists()) emptyList()
        else file.readLines().filter { it.isNotBlank() }
            .mapNotNull { runCatching { json.decodeFromString(MonitorEvent.serializer(), it) }.getOrNull() }

    /** Usuwa [count] najstarszych — wywoływane po potwierdzonej wysyłce. */
    @Synchronized
    fun removeFirst(count: Int) {
        if (!file.exists() || count <= 0) return
        val rest = file.readLines().filter { it.isNotBlank() }.drop(count)
        if (rest.isEmpty()) file.delete() else file.writeText(rest.joinToString(separator = "\n", postfix = "\n"))
    }

    @Synchronized
    fun size(): Int = if (!file.exists()) 0 else file.readLines().count { it.isNotBlank() }

    companion object {
        /** Górny limit zdarzeń w kolejce (ochrona przed nieskończonym rośnięciem, gdy serwer długo nieosiągalny). */
        const val MAX = 5000

        fun of(context: Context) = MonitorQueue(File(context.filesDir, "monitor/queue.jsonl"))
    }
}
