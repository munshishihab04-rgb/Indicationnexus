package com.personal.agent.core.logging

import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Room entity
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Log level severity, ordered from least to most severe.
 */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * A single structured log entry persisted in the Room `logs` table.
 *
 * @property id         Unique identifier (UUID string) generated at creation time.
 * @property level      Severity level: DEBUG, INFO, WARN, or ERROR.
 * @property module     Name of the module that emitted the log (e.g. "AppsModule").
 * @property event      Short machine-readable event key (e.g. "app_launched").
 * @property message    Human-readable description of the event.
 * @property dataJson   Optional JSON-encoded arbitrary key/value pairs for structured data.
 * @property createdAt  Unix epoch milliseconds when the entry was created.
 * @property uploadedAt Unix epoch milliseconds when the entry was successfully uploaded,
 *                      or null if it has not yet been uploaded.
 */
@Entity(tableName = "logs")
data class LogEntry(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "level")
    val level: String,           // LogLevel.name

    @ColumnInfo(name = "module")
    val module: String,

    @ColumnInfo(name = "event")
    val event: String,

    @ColumnInfo(name = "message")
    val message: String,

    @ColumnInfo(name = "data_json")
    val dataJson: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "uploaded_at")
    val uploadedAt: Long? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// DAO
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Room DAO for reading and writing [LogEntry] rows.
 */
@Dao
interface LogDao {

    /** Inserts a log entry. Replaces on conflict (duplicate primary key). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LogEntry)

    /** Returns all log entries that have not yet been uploaded, ordered oldest first. */
    @Query("SELECT * FROM logs WHERE uploaded_at IS NULL ORDER BY created_at ASC")
    suspend fun getPendingUploads(): List<LogEntry>

    /** Marks a set of log entries as uploaded at the given timestamp. */
    @Query("UPDATE logs SET uploaded_at = :uploadedAt WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<String>, uploadedAt: Long)

    /** Deletes log entries older than [olderThanMs] that have already been uploaded. */
    @Query("DELETE FROM logs WHERE uploaded_at IS NOT NULL AND created_at < :olderThanMs")
    suspend fun pruneUploaded(olderThanMs: Long)

    /** Returns the total number of log entries in the table. */
    @Query("SELECT COUNT(*) FROM logs")
    suspend fun count(): Int
}

// ─────────────────────────────────────────────────────────────────────────────
// AgentLogger
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Structured, non-blocking logger for the personal automation agent.
 *
 * Every log call:
 *  1. Writes a [LogEntry] to the Room `logs` table asynchronously (fire-and-forget
 *     coroutine launched in [scope]).
 *  2. Emits the same entry to Logcat for immediate developer visibility.
 *
 * Callers never wait for persistence to complete, so [debug], [info], [warn], and [error]
 * return immediately from the calling coroutine or thread.
 *
 * @param dao   Room [LogDao] used to persist entries.
 * @param scope [CoroutineScope] (typically the agent service scope) in which persistence
 *              coroutines are launched. All launches use [Dispatchers.IO].
 */
class AgentLogger(
    private val dao: LogDao,
    private val scope: CoroutineScope
) {

    companion object {
        private const val LOGCAT_TAG = "AgentLogger"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Logs a DEBUG-level entry. Intended for verbose diagnostic information that is
     * only useful during active development or troubleshooting.
     *
     * @param module  Identifier of the calling module (e.g. "AppsModule").
     * @param event   Short machine-readable event key (e.g. "scan_started").
     * @param message Human-readable description.
     * @param data    Optional additional key/value pairs serialised to JSON.
     */
    fun debug(module: String, event: String, message: String, data: Map<String, Any>? = null) {
        log(LogLevel.DEBUG, module, event, message, data)
    }

    /**
     * Logs an INFO-level entry. Use for significant lifecycle events and normal
     * operational milestones.
     *
     * @param module  Identifier of the calling module.
     * @param event   Short machine-readable event key.
     * @param message Human-readable description.
     * @param data    Optional additional key/value pairs serialised to JSON.
     */
    fun info(module: String, event: String, message: String, data: Map<String, Any>? = null) {
        log(LogLevel.INFO, module, event, message, data)
    }

    /**
     * Logs a WARN-level entry. Use when something unexpected happened but the system
     * can continue operating without intervention.
     *
     * @param module  Identifier of the calling module.
     * @param event   Short machine-readable event key.
     * @param message Human-readable description.
     * @param data    Optional additional key/value pairs serialised to JSON.
     */
    fun warn(module: String, event: String, message: String, data: Map<String, Any>? = null) {
        log(LogLevel.WARN, module, event, message, data)
    }

    /**
     * Logs an ERROR-level entry. Use for failures that require attention or that have
     * caused data loss / incorrect behaviour.
     *
     * @param module  Identifier of the calling module.
     * @param event   Short machine-readable event key.
     * @param message Human-readable description.
     * @param data    Optional additional key/value pairs serialised to JSON.
     */
    fun error(module: String, event: String, message: String, data: Map<String, Any>? = null) {
        log(LogLevel.ERROR, module, event, message, data)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Core log function. Builds a [LogEntry], emits it to Logcat synchronously, and
     * persists it to Room asynchronously using a fire-and-forget coroutine.
     */
    private fun log(
        level: LogLevel,
        module: String,
        event: String,
        message: String,
        data: Map<String, Any>?
    ) {
        val entry = LogEntry(
            id = UUID.randomUUID().toString(),
            level = level.name,
            module = module,
            event = event,
            message = message,
            dataJson = data?.let { serializeData(it) },
            createdAt = System.currentTimeMillis(),
            uploadedAt = null
        )

        // Emit to Logcat immediately — safe to call from any thread.
        emitToLogcat(level, module, event, message, entry.dataJson)

        // Persist to Room on IO dispatcher — fire-and-forget.
        scope.launch(Dispatchers.IO) {
            try {
                dao.insert(entry)
            } catch (e: Exception) {
                // If Room write fails, fall back to a Logcat error so we don't silently
                // drop the persistence failure itself.
                Log.e(LOGCAT_TAG, "[$module] Failed to persist log entry ${entry.id}: ${e.message}")
            }
        }
    }

    /**
     * Converts a [Map<String, Any>] to a compact JSON string using a minimal hand-rolled
     * serialiser to avoid a circular dependency on Moshi before it is available.
     *
     * Supported value types: String, Number, Boolean, null. Any other type is rendered via
     * [Any.toString] and stored as a JSON string.
     */
    private fun serializeData(data: Map<String, Any>): String {
        val sb = StringBuilder("{")
        var first = true
        for ((key, value) in data) {
            if (!first) sb.append(',')
            first = false
            sb.append('"').append(escapeJson(key)).append('"').append(':')
            when (value) {
                is String  -> sb.append('"').append(escapeJson(value)).append('"')
                is Number  -> sb.append(value.toString())
                is Boolean -> sb.append(value.toString())
                else       -> sb.append('"').append(escapeJson(value.toString())).append('"')
            }
        }
        sb.append('}')
        return sb.toString()
    }

    /**
     * Escapes characters that are special inside a JSON string literal.
     */
    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    /**
     * Writes the log entry to Logcat using the appropriate priority for [level].
     */
    private fun emitToLogcat(
        level: LogLevel,
        module: String,
        event: String,
        message: String,
        dataJson: String?
    ) {
        val logcatMessage = buildString {
            append("[$module][$event] ")
            append(message)
            if (dataJson != null) append(" | data=$dataJson")
        }
        when (level) {
            LogLevel.DEBUG -> Log.d(LOGCAT_TAG, logcatMessage)
            LogLevel.INFO  -> Log.i(LOGCAT_TAG, logcatMessage)
            LogLevel.WARN  -> Log.w(LOGCAT_TAG, logcatMessage)
            LogLevel.ERROR -> Log.e(LOGCAT_TAG, logcatMessage)
        }
    }
}
