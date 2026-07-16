package com.personal.agent.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.personal.agent.BuildConfig
import com.personal.agent.core.network.AgentApi
import com.personal.agent.core.network.BatteryInfo
import com.personal.agent.core.network.DevicePayload
import com.personal.agent.core.network.HeartbeatRequest
import com.personal.agent.core.network.MemoryInfo
import com.personal.agent.core.network.ModuleStatusInfo
import com.personal.agent.core.network.NetworkInfo
import com.personal.agent.core.network.RegisterRequest
import com.personal.agent.core.network.StorageInfo
import java.util.UUID

/**
 * Manages persistent device identity and provides helpers to build the
 * registration and heartbeat payloads required by the backend API.
 *
 * All sensitive values (device ID, install ID, API key) are stored in
 * [EncryptedSharedPreferences] backed by the Android Keystore.
 */
object DeviceManager {

    private const val TAG = "DeviceManager"

    // EncryptedSharedPreferences file name
    private const val PREFS_FILE = "agent_device_prefs"

    // Preference keys
    private const val KEY_DEVICE_ID  = "device_id"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_API_KEY    = "api_key"

    // Capability tags advertised to the server
    private val BASE_CAPABILITIES = listOf(
        "accessibility",
        "screenshot",
        "notifications",
        "foreground_service",
        "work_manager"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // EncryptedSharedPreferences helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Opens (or creates) the encrypted preferences file.
     * The MasterKey is AES256-GCM-backed by the Android Keystore.
     */
    private fun openPrefs(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Device / install identity
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the stable device UUID.  If one does not yet exist it is created,
     * persisted to [EncryptedSharedPreferences], and returned.
     *
     * This value is intentionally decoupled from Android's `Settings.Secure.ANDROID_ID`
     * so it survives factory resets on rooted devices and is consistent across
     * multi-user profiles.
     */
    fun getOrCreateDeviceId(context: Context): String {
        val prefs = openPrefs(context)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        Log.d(TAG, "Created new deviceId: $newId")
        return newId
    }

    /**
     * Returns the stable install UUID.  A new UUID is generated per fresh
     * install (i.e. when the app data is wiped).  Stored separately from the
     * device ID so the backend can distinguish re-installs from new devices.
     */
    fun getOrCreateInstallId(context: Context): String {
        val prefs = openPrefs(context)
        val existing = prefs.getString(KEY_INSTALL_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, newId).apply()
        Log.d(TAG, "Created new installId: $newId")
        return newId
    }

    /**
     * Returns the stored API key, or null if the device has not yet registered.
     */
    fun getApiKey(context: Context): String? =
        openPrefs(context).getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }

    /**
     * Persists the API key returned by the registration endpoint.
     */
    private fun saveApiKey(context: Context, apiKey: String) {
        openPrefs(context).edit().putString(KEY_API_KEY, apiKey).apply()
        Log.d(TAG, "API key saved.")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Payload builders
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Constructs the [DevicePayload] sent during device registration.
     *
     * Reads static device metadata from [Build] and the package manager.
     */
    fun buildRegistrationPayload(
        context: Context,
        deviceId: String,
        installId: String
    ): DevicePayload {
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }

        val appVersion = packageInfo?.versionName ?: BuildConfig.VERSION_NAME
        val appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode?.toInt() ?: BuildConfig.VERSION_CODE
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode ?: BuildConfig.VERSION_CODE
        }

        return DevicePayload(
            deviceId       = deviceId,
            installId      = installId,
            appVersion     = appVersion,
            appVersionCode = appVersionCode,
            manufacturer   = Build.MANUFACTURER,
            model          = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdk            = Build.VERSION.SDK_INT,
            capabilities   = buildCapabilities(context),
            createdAt      = System.currentTimeMillis()
        )
    }

    /**
     * Builds the [HeartbeatRequest] by reading live system metrics.
     *
     * Queries:
     * - [BatteryManager] for charge level, temperature, and charging state
     * - [PowerManager] for power-save mode
     * - [ConnectivityManager] for network transport and metering
     * - [StatFs] for internal storage figures
     * - [ActivityManager] for heap/RAM availability
     */
    fun buildHeartbeatPayload(
        context: Context,
        deviceId: String,
        configVersion: Int,
        modules: List<ModuleStatusInfo> = emptyList(),
        queueDepth: Int = 0
    ): HeartbeatRequest {
        return HeartbeatRequest(
            deviceId      = deviceId,
            timestamp     = System.currentTimeMillis(),
            battery       = readBatteryInfo(context),
            network       = readNetworkInfo(context),
            storage       = readStorageInfo(),
            memory        = readMemoryInfo(context),
            modules       = modules,
            queueDepth    = queueDepth,
            configVersion = configVersion
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Registration
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Performs the full device registration flow:
     * 1. Retrieves (or creates) the device and install IDs.
     * 2. Calls [AgentApi.registerDevice].
     * 3. On success, persists the returned API key to [EncryptedSharedPreferences].
     *
     * @param api     A [AgentApi] instance built **without** an API key
     *                (pass null to [NetworkClient]).
     * @param context Android [Context] used for shared-preferences and package info.
     * @param setupToken Optional one-time setup token issued by the server admin.
     *
     * @return `true` if registration succeeded and the API key was saved;
     *         `false` otherwise (network error, server rejection, etc.).
     */
    suspend fun register(
        api: AgentApi,
        context: Context,
        setupToken: String? = null
    ): Boolean {
        val deviceId  = getOrCreateDeviceId(context)
        val installId = getOrCreateInstallId(context)
        val payload   = buildRegistrationPayload(context, deviceId, installId)

        val request = RegisterRequest(
            setupToken = setupToken,
            device     = payload
        )

        return try {
            val response = api.registerDevice(request)
            if (response.isSuccessful) {
                val body = response.body()
                val registerData = body?.data
                if (body?.ok == true && registerData != null) {
                    saveApiKey(context, registerData.apiKey)
                    Log.i(TAG, "Device registered successfully. deviceId=${registerData.deviceId}")
                    true
                } else {
                    Log.w(TAG, "Registration response not ok: $body")
                    false
                }
            } else {
                Log.w(TAG, "Registration HTTP error: ${response.code()} ${response.message()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Registration network exception", e)
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers — system metric readers
    // ─────────────────────────────────────────────────────────────────────────

    private fun readBatteryInfo(context: Context): BatteryInfo {
        val batteryIntent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level   = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale   = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (scale > 0) ((level.toFloat() / scale) * 100).toInt() else -1

        val status  = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                       status == BatteryManager.BATTERY_STATUS_FULL

        // Temperature is reported in tenths of a degree Celsius
        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC   = tempRaw / 10.0f

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val powerSave    = powerManager?.isPowerSaveMode ?: false

        return BatteryInfo(
            percent      = percent,
            charging     = charging,
            temperatureC = tempC,
            powerSaveMode = powerSave
        )
    }

    private fun readNetworkInfo(context: Context): NetworkInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkInfo(transport = "unknown", metered = false, connected = false)

        val network = cm.activeNetwork
        val caps    = cm.getNetworkCapabilities(network)

        if (network == null || caps == null) {
            return NetworkInfo(transport = "none", metered = false, connected = false)
        }

        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)      -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)  -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)  -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)       -> "vpn"
            else -> "other"
        }

        val connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        return NetworkInfo(
            transport = transport,
            metered   = metered,
            connected = connected
        )
    }

    private fun readStorageInfo(): StorageInfo {
        val stat = StatFs(Environment.getDataDirectory().path)
        val blockSize  = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val freeBlocks  = stat.availableBlocksLong

        return StorageInfo(
            totalBytes = totalBlocks * blockSize,
            freeBytes  = freeBlocks  * blockSize
        )
    }

    private fun readMemoryInfo(context: Context): MemoryInfo {
        val am   = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)

        return MemoryInfo(
            totalBytes     = info.totalMem,
            availableBytes = info.availMem,
            lowMemory      = info.lowMemory
        )
    }

    /**
     * Builds the capability list advertised during registration.
     * Extend this list as additional modules are added to the agent.
     */
    private fun buildCapabilities(context: Context): List<String> {
        val caps = mutableListOf<String>()
        caps.addAll(BASE_CAPABILITIES)

        // Advertise camera only if the device has one
        if (context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) {
            caps.add("camera")
        }

        // Advertise NFC
        if (context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_NFC)) {
            caps.add("nfc")
        }

        // Advertise biometrics
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_FINGERPRINT)) {
            caps.add("fingerprint")
        }

        return caps.distinct()
    }
}
