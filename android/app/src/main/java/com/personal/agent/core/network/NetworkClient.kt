package com.personal.agent.core.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Builds and caches a fully configured [AgentApi] Retrofit instance.
 *
 * @param baseUrl  Root server URL, e.g. `"https://api.example.com/"`.
 *                 Must end with `/` (Retrofit requirement).
 * @param apiKey   Bearer token obtained after device registration.
 *                 Pass `null` before registration; the Authorization header
 *                 is omitted in that case so the register endpoint can be
 *                 reached without credentials.
 * @param deviceId The stable device identifier to attach on every request as
 *                 the `X-Device-Id` header. May be empty string before the ID
 *                 is first created.
 */
class NetworkClient(
    private val baseUrl: String,
    private val apiKey: String?,
    private val deviceId: String = ""
) {

    // ─────────────────────────────────────────────────────────────────────────
    // Moshi
    // ─────────────────────────────────────────────────────────────────────────

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(Date::class.java, Rfc3339DateJsonAdapter().nullSafe())
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OkHttp interceptors
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Injects authentication and tracing headers on every outgoing request.
     *
     * Headers added:
     * - `Content-Type: application/json` (via Retrofit's converter, but we
     *   pin it here for non-body requests too)
     * - `Authorization: Bearer <apiKey>` — only when [apiKey] is non-null
     * - `X-Device-Id: <deviceId>` — always present (may be empty before first boot)
     * - `X-Request-Id: <random UUID>` — unique per request for tracing
     */
    private val authInterceptor = okhttp3.Interceptor { chain ->
        val originalRequest: Request = chain.request()
        val builder = originalRequest.newBuilder()
            .header("X-Device-Id", deviceId)
            .header("X-Request-Id", UUID.randomUUID().toString())

        apiKey?.let { key ->
            builder.header("Authorization", "Bearer $key")
        }

        chain.proceed(builder.build())
    }

    /**
     * Verbose HTTP logging — only active in debug builds.
     * The level is set to [HttpLoggingInterceptor.Level.BODY] so that request
     * and response bodies are visible during development.
     */
    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (com.personal.agent.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OkHttp client
    // ─────────────────────────────────────────────────────────────────────────

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Auth + tracing headers first, then logging (so logged request
            // already contains the injected headers).
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            // Timeouts
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Retrofit
    // ─────────────────────────────────────────────────────────────────────────

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates and returns a concrete implementation of [AgentApi].
     * The instance is backed by Retrofit and inherits all OkHttp interceptors
     * and timeout settings configured above.
     */
    fun buildApi(): AgentApi = retrofit.create(AgentApi::class.java)

    // ─────────────────────────────────────────────────────────────────────────
    // Companion — static factories
    // ─────────────────────────────────────────────────────────────────────────

    companion object {

        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS    = 60L
        private const val WRITE_TIMEOUT_SECONDS   = 120L

        /**
         * Create a [NetworkClient] suitable for the initial device registration
         * call, where no API key is available yet.
         *
         * @param baseUrl    Server root URL.
         * @param deviceId   Stable device UUID (may be freshly generated).
         */
        fun forRegistration(
            baseUrl: String,
            deviceId: String
        ): NetworkClient = NetworkClient(
            baseUrl  = baseUrl,
            apiKey   = null,
            deviceId = deviceId
        )

        /**
         * Create a [NetworkClient] for all post-registration API calls.
         *
         * @param baseUrl    Server root URL.
         * @param apiKey     Bearer token saved after successful registration.
         * @param deviceId   Stable device UUID.
         */
        fun forAuthenticatedCalls(
            baseUrl: String,
            apiKey: String,
            deviceId: String
        ): NetworkClient = NetworkClient(
            baseUrl  = baseUrl,
            apiKey   = apiKey,
            deviceId = deviceId
        )

        /**
         * Convenience: create a [NetworkClient] using [com.personal.agent.BuildConfig.SERVER_BASE_URL]
         * as the base URL.
         *
         * @param apiKey   Nullable — pass null before registration.
         * @param deviceId Stable device UUID.
         */
        fun fromBuildConfig(
            apiKey: String?,
            deviceId: String
        ): NetworkClient = NetworkClient(
            baseUrl  = com.personal.agent.BuildConfig.SERVER_BASE_URL,
            apiKey   = apiKey,
            deviceId = deviceId
        )
    }
}
