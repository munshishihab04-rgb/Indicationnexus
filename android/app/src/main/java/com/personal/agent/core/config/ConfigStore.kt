package com.personal.agent.core.config

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

// ─────────────────────────────────────────────────────────────────────────────
// RemoteConfig data class
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The canonical remote configuration snapshot received from the backend and cached locally.
 *
 * All JSON blobs (modules, allowlist, denylist, privacy) are stored as raw strings so the
 * individual module parsers can interpret them without introducing a central schema dependency.
 *
 * @property version            Monotonically increasing config version from the server.
 * @property modulesJson        JSON object keyed by module id, each value a serialised
 *                              [com.personal.agent.core.modules.ModuleConfig].
 * @property heartbeatSeconds   How often the agent service should send a heartbeat ping.
 * @property configSyncMinutes  How often the agent should poll for a new remote config.
 * @property logUploadMinutes   How often pending [com.personal.agent.core.logging.LogEntry]
 *                              rows should be flushed to the server.
 * @property appsAllowlistJson  JSON array of package names explicitly permitted to be tracked.
 * @property appsDenylistJson   JSON array of package names that must never be tracked.
 * @property privacyJson        JSON object carrying privacy-related toggles and settings.
 * @property updatedAt          Unix epoch milliseconds when this config was received and stored.
 */
data class RemoteConfig(
    val version: Int,
    val modulesJson: String,
    val heartbeatSeconds: Int,
    val configSyncMinutes: Int,
    val logUploadMinutes: Int,
    val appsAllowlistJson: String,
    val appsDenylistJson: String,
    val privacyJson: String,
    val updatedAt: Long
)

// ─────────────────────────────────────────────────────────────────────────────
// Room entity — single-row table; always uses id = 1
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Room entity backing [RemoteConfig]. The table always contains at most one row
 * (primary key fixed to 1) so queries never need a WHERE clause.
 */
@Entity(tableName = "remote_config")
data class RemoteConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1,

    @ColumnInfo(name = "version")
    val version: Int,

    @ColumnInfo(name = "modules_json")
    val modulesJson: String,

    @ColumnInfo(name = "heartbeat_seconds")
    val heartbeatSeconds: Int,

    @ColumnInfo(name = "config_sync_minutes")
    val configSyncMinutes: Int,

    @ColumnInfo(name = "log_upload_minutes")
    val logUploadMinutes: Int,

    @ColumnInfo(name = "apps_allowlist_json")
    val appsAllowlistJson: String,

    @ColumnInfo(name = "apps_denylist_json")
    val appsDenylistJson: String,

    @ColumnInfo(name = "privacy_json")
    val privacyJson: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

// ─────────────────────────────────────────────────────────────────────────────
// DAO
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Room DAO for reading and writing the single cached [RemoteConfigEntity] row.
 */
@Dao
interface RemoteConfigDao {

    /**
     * Returns the cached config entity, or null if none has been saved yet.
     */
    @Query("SELECT * FROM remote_config WHERE id = 1 LIMIT 1")
    suspend fun get(): RemoteConfigEntity?

    /**
     * Inserts or fully replaces the cached config row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RemoteConfigEntity)
}

// ─────────────────────────────────────────────────────────────────────────────
// ConfigStore
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Thin wrapper around the [RemoteConfigDao] that translates between the Room entity
 * representation and the public [RemoteConfig] data class.
 *
 * Callers interact only with [RemoteConfig] and never need to know about Room internals.
 *
 * **Staleness**: a config is considered stale when [RemoteConfig.updatedAt] is more than
 * [STALE_THRESHOLD_MS] (30 minutes) in the past. The config sync worker should re-fetch
 * whenever [isStale] returns true.
 *
 * @param dao The Room DAO (typically obtained from [com.personal.agent.core.db.AgentDatabase]).
 */
class ConfigStore(private val dao: RemoteConfigDao) {

    companion object {
        /** A cached config older than this is treated as stale and should be refreshed. */
        const val STALE_THRESHOLD_MS: Long = 30L * 60L * 1_000L   // 30 minutes
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reads the most-recently-saved [RemoteConfig] from the database.
     *
     * @return The cached [RemoteConfig], or `null` if no config has been stored yet.
     */
    suspend fun getConfig(): RemoteConfig? {
        return dao.get()?.toDomain()
    }

    /**
     * Persists [config] to the database, replacing any previously stored value.
     *
     * [RemoteConfig.updatedAt] is stored as-is. Callers should set it to
     * `System.currentTimeMillis()` when constructing the config from a fresh server response.
     *
     * @param config The [RemoteConfig] to persist.
     */
    suspend fun saveConfig(config: RemoteConfig) {
        dao.upsert(config.toEntity())
    }

    /**
     * Returns `true` when the cached config is absent or older than [STALE_THRESHOLD_MS].
     *
     * A `null` config (no config ever saved) is always considered stale so the first
     * sync happens immediately on startup.
     */
    suspend fun isStale(): Boolean {
        val entity = dao.get() ?: return true
        val ageMs = System.currentTimeMillis() - entity.updatedAt
        return ageMs > STALE_THRESHOLD_MS
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapping helpers (entity ↔ domain)
    // ─────────────────────────────────────────────────────────────────────────

    private fun RemoteConfigEntity.toDomain(): RemoteConfig = RemoteConfig(
        version = version,
        modulesJson = modulesJson,
        heartbeatSeconds = heartbeatSeconds,
        configSyncMinutes = configSyncMinutes,
        logUploadMinutes = logUploadMinutes,
        appsAllowlistJson = appsAllowlistJson,
        appsDenylistJson = appsDenylistJson,
        privacyJson = privacyJson,
        updatedAt = updatedAt
    )

    private fun RemoteConfig.toEntity(): RemoteConfigEntity = RemoteConfigEntity(
        id = 1,  // single-row table
        version = version,
        modulesJson = modulesJson,
        heartbeatSeconds = heartbeatSeconds,
        configSyncMinutes = configSyncMinutes,
        logUploadMinutes = logUploadMinutes,
        appsAllowlistJson = appsAllowlistJson,
        appsDenylistJson = appsDenylistJson,
        privacyJson = privacyJson,
        updatedAt = updatedAt
    )
}
