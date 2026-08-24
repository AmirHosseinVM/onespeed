package ir.onespeed.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SpeedtestManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val FLAG_REGEX = Regex("[\uD83C\uDDE6-\uD83C\uDDFF]{2}")

enum class VpnState { IDLE, CONNECTING, CONNECTED }

object ConnectionManager {
    private const val SUB_ID = "onespeed"

    fun importSub(rawBody: String): Int {
        val (count, _) = AngConfigManager.importBatchConfig(rawBody, SUB_ID, false)
        return count
    }

    fun listServers(): List<ServerInfo> {
        val guids = MmkvManager.decodeServerList(SUB_ID)
        return guids.mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            if (profile.serverPort == "1") return@mapNotNull null
            val flagMatch = FLAG_REGEX.find(profile.remarks)
            val cleanName = profile.remarks.replace(FLAG_REGEX, "").replace(Regex("[◆❖✅]"), "").trim()
            ServerInfo(
                guid = guid,
                name = cleanName.ifEmpty { profile.remarks },
                flagCode = flagMatch?.value?.let { flagEmojiToCode(it) },
            )
        }
    }

    private fun flagEmojiToCode(emoji: String): String? {
        val points = emoji.codePoints().toArray()
        if (points.size != 2) return null
        return points.joinToString("") { (it - 0x1F1E6 + 'A'.code).toChar().toString() }.lowercase()
    }

    suspend fun ping(guid: String): Long? = withContext(Dispatchers.IO) {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return@withContext null
        val port = profile.serverPort?.toIntOrNull() ?: return@withContext null
        val server = profile.server ?: return@withContext null
        val result = SpeedtestManager.socketConnectTime(server, port)
        if (result < 0) null else result
    }

    fun connect(context: Context, guid: String) {
        // DEBUG: shows exactly what's stored for this guid on-screen so we
        // can see the real cause instead of guessing. Remove once fixed.
        val profile = MmkvManager.decodeServerConfig(guid)
        if (profile == null) {
            android.widget.Toast.makeText(
                context, "DEBUG: no profile stored for guid=$guid", android.widget.Toast.LENGTH_LONG,
            ).show()
        } else {
            android.widget.Toast.makeText(
                context,
                "DEBUG: server=${profile.server} port=${profile.serverPort} type=${profile.configType} remarks=${profile.remarks}",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }

        MmkvManager.setSelectServer(guid)
        LauncherManager.startService(context)
    }

    fun disconnect(context: Context) {
        LauncherManager.stopService(context)
    }

    fun registerStatusReceiver(context: Context, onState: (VpnState, String?) -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val what = intent?.getIntExtra("key", -1) ?: return
                when (what) {
                    AppConfig.MSG_STATE_START -> onState(VpnState.CONNECTING, null)
                    AppConfig.MSG_STATE_START_SUCCESS, AppConfig.MSG_STATE_RUNNING ->
                        onState(VpnState.CONNECTED, null)
                    AppConfig.MSG_STATE_STOP_SUCCESS, AppConfig.MSG_STATE_NOT_RUNNING ->
                        onState(VpnState.IDLE, null)
                    AppConfig.MSG_STATE_START_FAILURE -> {
                        val err = intent.getSerializableExtra("content") as? String
                        onState(VpnState.IDLE, err ?: "اتصال ناموفق بود")
                    }
                }
            }
        }
        val filter = IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        return receiver
    }
}
