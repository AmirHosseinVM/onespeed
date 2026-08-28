package ir.onespeed.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Looks up the device's current public IP. Called only while the VPN is
 * connected, so — since this app has no split-tunneling/bypass rules — the
 * request itself goes through the tunnel and the address returned is the
 * real exit IP, not a guess or a cached value.
 */
object IpLookup {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun currentIp(): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.ipify.org").build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                res.body?.string()?.trim()?.takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            null
        }
    }
}
