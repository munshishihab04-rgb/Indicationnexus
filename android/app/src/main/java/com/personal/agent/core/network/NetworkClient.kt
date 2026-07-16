package com.personal.agent.core.network

import com.personal.agent.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Builds the Retrofit API client.
 * Pass apiKey = null for the registration call (no auth yet).
 * Pass apiKey + deviceId for all subsequent authenticated calls.
 */
class NetworkClient(
    private val baseUrl: String,
    private val apiKey: String? = null,
    private val deviceId: String? = null
) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private fun authInterceptor(): Interceptor = Interceptor { chain ->
        val requestId = UUID.randomUUID().toString()
        val original = chain.request()
        val builder = original.newBuilder()
            .header("X-Request-Id", requestId)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")

        apiKey?.let { builder.header("Authorization", "Bearer $it") }
        deviceId?.let { builder.header("X-Device-Id", it) }

        chain.proceed(builder.build())
    }

    private fun buildOkHttp(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
        }

        return builder.build()
    }

    fun buildApi(): AgentApi = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(buildOkHttp())
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(AgentApi::class.java)

    companion object {
        /** Unauthenticated client — for device registration only. */
        fun forRegistration(baseUrl: String): AgentApi =
            NetworkClient(baseUrl).buildApi()

        /** Authenticated client — for all post-enrollment calls. */
        fun forDevice(baseUrl: String, apiKey: String, deviceId: String): AgentApi =
            NetworkClient(baseUrl, apiKey, deviceId).buildApi()
    }
}
