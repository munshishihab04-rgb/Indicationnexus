package com.personal.agent.core

import android.content.Context
import android.util.Log
import com.personal.agent.core.db.AgentDatabase
import com.personal.agent.core.network.NetworkClient
import com.personal.agent.device.DeviceManager

private const val TAG = "AgentSession"

/**
 * Provides authenticated API access for workers.
 * Returns null when the device is not yet enrolled (no API key).
 */
internal object AgentSession {

    data class Session(
        val api: com.personal.agent.core.network.AgentApi,
        val deviceId: String,
        val db: AgentDatabase
    )

    fun get(context: Context): Session? {
        val deviceId = DeviceManager.getOrCreateDeviceId(context)
        val apiKey = DeviceManager.getApiKey(context)
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "Device not enrolled — apiKey missing, skipping worker")
            return null
        }
        val api = NetworkClient.forDevice(
            baseUrl = com.personal.agent.BuildConfig.SERVER_BASE_URL,
            apiKey = apiKey,
            deviceId = deviceId
        )
        val db = AgentDatabase.getInstance(context)
        return Session(api, deviceId, db)
    }
}
