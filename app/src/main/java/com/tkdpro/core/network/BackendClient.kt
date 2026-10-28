package com.tkdpro.core.network

import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.security.SecretStore
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class BackendError(message: String) : Exception(message) {
    /** No backend URL or token configured: the feature is unavailable, not broken. */
    data object NotConfigured : BackendError("Backend not configured")
    data object Unauthorized : BackendError("Unauthorized")
    data class Network(val causeMessage: String?) : BackendError("Network error: $causeMessage")
    data class Server(val code: Int, val detail: String?) : BackendError("Server error $code: $detail")
    data class ServiceUnavailable(val detail: String?) : BackendError("Service unavailable: $detail")
}

/**
 * Builds a Retrofit client for the owner's configured backend. The base URL comes from settings,
 * so the client is rebuilt when it changes. HTTPS is enforced by the network security config,
 * except for local development hosts.
 */
@Singleton
class BackendClient @Inject constructor(
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    val json: Json,
) {
    @Volatile private var cached: Pair<String, BackendApi>? = null

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = secrets.get(SecretStore.BACKEND_TOKEN).orEmpty()
                chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
            }
            .build()
    }

    suspend fun isConfigured(): Boolean =
        isAllowedUrl(settings.current().backendUrl) && !secrets.get(SecretStore.BACKEND_TOKEN).isNullOrBlank()

    suspend fun api(): BackendApi {
        val url = settings.current().backendUrl
        if (!isAllowedUrl(url) || secrets.get(SecretStore.BACKEND_TOKEN).isNullOrBlank()) {
            throw BackendError.NotConfigured
        }
        cached?.let { (u, api) -> if (u == url) return api }
        val api = build(url)
        cached = url to api
        return api
    }

    private fun build(url: String): BackendApi = Retrofit.Builder()
        .baseUrl("$url/")
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(BackendApi::class.java)

    /** Pairs this phone with the TKD PRO server at [url] using the 6-digit code shown on the computer. */
    suspend fun pair(url: String, code: String, deviceName: String): Result<String> {
        val clean = url.trim().trimEnd('/')
        if (!isAllowedUrl(clean)) return Result.failure(BackendError.Server(-1, "Insecure address"))
        // Not through call(): the phone has no token yet, which is exactly what pairing provides.
        return mapErrors { build(clean).pair(PairRequest(code.trim(), deviceName)) }
            .mapCatching { it.token ?: throw BackendError.Server(-1, "No token") }
    }

    companion object {
        /**
         * HTTPS anywhere; plain HTTP only inside the home network or a private VPN, where the
         * owner's computer runs the server: loopback, 10/8, 172.16/12, 192.168/16, 100.64/10 (Tailscale), *.local.
         */
        fun isAllowedUrl(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            if (parsed.isHttps) return true
            val host = parsed.host
            if (host == "localhost" || host.endsWith(".local")) return true
            val parts = host.split(".").mapNotNull { it.toIntOrNull() }
            if (parts.size != 4 || parts.any { it !in 0..255 }) return false
            val (a, b) = parts
            return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 100 && b in 64..127)
        }
    }

    /** Runs a backend call and maps transport failures to [BackendError]. */
    suspend fun <T> call(block: suspend (BackendApi) -> T): Result<T> = mapErrors { block(api()) }

    private suspend fun <T> mapErrors(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: BackendError) {
        Result.failure(e)
    } catch (e: HttpException) {
        val detail = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        Result.failure(
            when (e.code()) {
                401, 403 -> BackendError.Unauthorized
                503 -> BackendError.ServiceUnavailable(detail)
                else -> BackendError.Server(e.code(), detail)
            },
        )
    } catch (e: IOException) {
        Result.failure(BackendError.Network(e.message))
    } catch (e: kotlinx.serialization.SerializationException) {
        Result.failure(BackendError.Server(-1, e.message))
    }
}
