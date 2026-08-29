package ir.onespeed.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class ApiResult {
    data class Success(val rawBody: String, val plan: PlanInfo) : ApiResult()
    data class DeviceLimit(val limit: Int?) : ApiResult()
    data class Error(val message: String) : ApiResult()
}

object ApiService {
    // The app talks ONLY to this gateway — never to the real panel domain directly.
    const val GATEWAY_BASE = "https://dl90music.ir/app"

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
     *
     * Does a real JSON parse (org.json, built into Android — no extra
     * dependency) instead of scanning raw text with regex: a text scan can't
     * tell which "address"/"port" belong to which entry once objects are
     * nested (address lives under settings.vnext[0] or settings.servers[0],
     * not flat on the object), so it silently grabbed the wrong value or none
     * at all. Walking the real structure mirrors exactly how the server list
     * itself is parsed, so this and the visible server list can never disagree.
     */
    private fun extractPlan(rawBody: String): PlanInfo {
        var days: String? = null
        var vol: String? = null
        var expiry: String? = null

        val trimmed = rawBody.trim()
        if (trimmed.startsWith("[")) {
            try {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val entry = arr.optJSONObject(i) ?: continue
                    val remarks = entry.optString("remarks", "")
                    val outbounds = entry.optJSONArray("outbounds") ?: continue
                    var address: String? = null
                    var port: Int? = null
                    for (j in 0 until outbounds.length()) {
                        val ob = outbounds.optJSONObject(j) ?: continue
                        if (ob.optString("tag") != "proxy") continue
                        val settings = ob.optJSONObject("settings") ?: continue
                        val vnext = settings.optJSONArray("vnext")
                        val serversArr = settings.optJSONArray("servers")
                        val target = vnext?.optJSONObject(0) ?: serversArr?.optJSONObject(0)
                        if (target != null) {
                            address = if (target.has("address")) target.getString("address") else null
                            port = if (target.has("port")) target.optInt("port") else null
                        } else if (settings.has("address")) {
                            address = settings.getString("address")
                            port = if (settings.has("port")) settings.optInt("port") else null
                        }
                        break
                    }
                    if (port != 1) continue
                    dayRegex.find(remarks)?.let { days = days ?: it.groupValues[1].trim() }
                    volRegex.find(remarks)?.let { vol = vol ?: it.groupValues[1].trim() }
                    address?.let { a -> dateRegex.find(a)?.let { expiry = expiry ?: it.groupValues[1] } }
                }
            } catch (e: Exception) {
                // Malformed/unexpected JSON shape — fall through with whatever was found (possibly nothing).
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
