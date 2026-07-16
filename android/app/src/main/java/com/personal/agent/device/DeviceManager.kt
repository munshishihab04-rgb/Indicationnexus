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
import com.personal.agent.core.network.NetworkClient
import com.personal.agent.core.network.RegisterRequest
import com.personal.agent.core.network.StorageInfo
import java.util.UUID

private const val TAG = "DeviceManager"
private const val PREFS_FILE = "agent_device_secure"
private const val KEY_DEVICE_ID = "device_id"
private const val KEY_INSTALL_ID = "install_id"
private const val KEY_API_KEY = "api_key"

object DeviceManager {

    // ─── Identity ─────────────────────────────────────────────────────────────

    fun getOrCreateDeviceId(context: Context): String =
        getOrCreate(context, KEY_DEVICE_ID)

    fun getOrCreateInstallId(context: Context): String =
        getOrCreate(context, KEY_INSTALL_ID)

    fun getApiKey(context: Context): String? =
        securePrefs(context).getString(KEY_API_KEY, null)

    fun saveApiKey(context: Context, apiKey: String) {
        securePrefs(context).edit().putString(KEY_API_KEY, apiKey).apply()
    }

    // ─── Payloads ─────────────────────────────────────────────────────────────

    fun buildRegistrationPayload(
        context: Context,
        deviceId: String,
        installId: String
    ): DevicePayload = DevicePayload(
        deviceId = deviceId,
        installId = installId,
        appVersion = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        androidVersion = Build.VERSION.RELEASE,
        sdk = Build.VERSION.SDK_INT,
        capabilities = detectCapabilities(context),
        createdAt = System.currentTimeMillis()
    )

    fun buildHeartbeatPayload(
        context: Context,
        deviceId: String,
        moduleStatuses: List<ModuleStatusInfo> = emptyList(),
        configVersion: Int = 0,
        jobQueueDepth: Int = 0
    ): HeartbeatRequest = HeartbeatRequest(
        deviceId = deviceId,
        timestamp = System.currentTimeMillis(),
        battery = readBattery(context),
        network = readNetwork(context),
        storage = readStorage(),
        memory = readMemory(context),
        modules = moduleStatuses,
        queueDepth = jobQueueDepth,
        configVersion = configVersion
    )

    // ─── Enrollment ───────────────────────────────────────────────────────────

    suspend fun register(context: Context, setupToken: String): Boolean {
        val deviceId = getOrCreateDeviceId(context)
        val installId = getOrCreateInstallId(context)
        val api = NetworkClient.forRegistration(BuildConfig.SERVER_BASE_URL)

        return try {
            val resp = api.registerDevice(
                RegisterRequest(
                    setupToken = setupToken,
                    device = buildRegistrationPayload(context, deviceId, installId)
                )
            )
            if (resp.ok && resp.data != null) {
                saveApiKey(context, resp.data.apiKey)
                Log.i(TAG, "Device enrolled: ${resp.data.deviceId}")
                true
            } else {
                Log.w(TAG, "Enrollment failed: ok=${resp.ok}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Enrollment error: ${e.message}")
            false
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun detectCapabilities(context: Context): List<String> {
        val caps = mutableListOf("heartbeat", "config", "logs", "commands")
        // Phase 4+: add when services are enabled
        // caps.add("notifications")
        // caps.add("accessibility")
        // caps.add("ocr")
        return caps
    }

    private fun readBattery(context: Context): BatteryInfo {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val powerSave = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            pm.isPowerSaveMode
        } else false

        return BatteryInfo(
            percent = percent,
            charging = charging,
            temperatureC = tempTenths / 10f,
            powerSaveMode = powerSave
        )
    }

    private fun readNetwork(context: Context): NetworkInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val connected = caps != null
        val metered = cm.isActiveNetworkMetered
        val transport = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
            else -> "unknown"
        }
        return NetworkInfo(transport = transport, metered = metered, connected = connected)
    }

    private fun readStorage(): StorageInfo {
        val stat = StatFs(Environment.getDataDirectory().path)
        return StorageInfo(
            totalBytes = stat.totalBytes,
            freeBytes = stat.freeBytes
        )
    }

    private fun readMemory(context: Context): MemoryInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return MemoryInfo(
            totalBytes = mi.totalMem,
            availableBytes = mi.availMem,
            lowMemory = mi.lowMemory
        )
    }

    private fun getOrCreate(context: Context, key: String): String {
        val prefs = securePrefs(context)
        return prefs.getString(key, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(key, it).apply()
        }
    }

    private fun securePrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
