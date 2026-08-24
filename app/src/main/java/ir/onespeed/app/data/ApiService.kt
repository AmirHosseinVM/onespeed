package ir.onespeed.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed class ApiResult {
    data class Success(val rawBody: String, val plan: PlanInfo) : ApiResult()
    data class DeviceLimit(val limit: Int?) : ApiResult()
    data class Error(val message: String) : ApiResult()
}

object ApiService {
    // The app talks ONLY to this gateway — never to the real panel domain directly.
    const val GATEWAY_BASE = "https://devfull.sbs/app"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val dayRegex = Regex("""روز\s*:\s*([^|]+)""")
    private val volRegex = Regex("""حجم\s*:\s*([^|]+)""")
    private val dateRegex = Regex("""^(\d{4}-\d{2}-\d{2})""")

    suspend fun fetchSub(deviceId: String, token: String): ApiResult = withContext(Dispatchers.IO) {
        try {
            val url = "$GATEWAY_BASE/sub.php?token=${java.net.URLEncoder.encode(token, "UTF-8")}"
            val req = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .addHeader("X-Device-Id", deviceId)
                .build()

            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                when {
                    res.code == 403 -> {
                        val limit = Regex(""""limit"\s*:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
                        ApiResult.DeviceLimit(limit)
                    }
                    !res.isSuccessful -> ApiResult.Error("http_${res.code}")
                    else -> ApiResult.Success(body, extractPlan(body))
                }
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "network_error")
        }
    }

    /**
     * Extracts OneSpeed's own remaining-day/volume/expiry convention — this
     * is business-specific metadata the panel hides inside a fake "server"
     * entry (port == 1). It has nothing to do with actual server parsing,
     * which AngConfigManager.importBatchConfig handles for every real entry.
     */
    private fun extractPlan(rawBody: String): PlanInfo {
        var days: String? = null
        var vol: String? = null
        var expiry: String? = null

        val trimmed = rawBody.trim()
        if (trimmed.startsWith("[")) {
            // JSON-array format: scan remarks/address text directly with the same regexes —
            // avoids a full JSON parse just for two fields.
            Regex(""""remarks"\s*:\s*"([^"]*)"[\s\S]*?"address"\s*:\s*"([^"]*)"""").findAll(trimmed).forEach { m ->
                val remarks = m.groupValues[1]
                val address = m.groupValues[2]
                dayRegex.find(remarks)?.let { days = days ?: it.groupValues[1].trim() }
                volRegex.find(remarks)?.let { vol = vol ?: it.groupValues[1].trim() }
                dateRegex.find(address)?.let { expiry = expiry ?: it.groupValues[1] }
            }
        } else {
            trimmed.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val hashIdx = line.indexOf('#')
                if (hashIdx < 0) return@forEach
                val remarks = try {
                    java.net.URLDecoder.decode(line.substring(hashIdx + 1), "UTF-8")
                } catch (e: Exception) { "" }
                val hostPortMatch = Regex("""^\w+://(?:[^@]+@)?([^:/?#]+):(\d+)""").find(line) ?: return@forEach
                val address = hostPortMatch.groupValues[1]
                val port = hostPortMatch.groupValues[2].toIntOrNull() ?: return@forEach
                if (port != 1) return@forEach
                dayRegex.find(remarks)?.let { days = days ?: it.groupValues[1].trim() }
                volRegex.find(remarks)?.let { vol = vol ?: it.groupValues[1].trim() }
                dateRegex.find(address)?.let { expiry = expiry ?: it.groupValues[1] }
            }
        }
        return PlanInfo(daysText = days, volumeText = vol, expiryDate = expiry)
    }
}
