package ir.onespeed.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.v2ray.ang.handler.MmkvManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A plain-text diagnostic log the person can pull up and share without any
 * special tooling (no adb, no root, no logcat access) — every connect
 * attempt (and its outcome) gets appended here, so a failure can be copied
 * and sent for debugging straight from the phone.
 */
object DiagnosticLog {
    private const val FILE_NAME = "onespeed_debug_log.txt"
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun logFile(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun append(context: Context, line: String) {
        try {
            logFile(context).appendText("[${timeFormat.format(Date())}] $line\n")
        } catch (e: Exception) {
            // Logging must never crash the app.
        }
    }

    /** Called right before attempting to bring the tunnel up. */
    fun logConnectAttempt(context: Context, guid: String) {
        val p = MmkvManager.decodeServerConfig(guid)
        if (p == null) {
            append(context, "CONNECT ATTEMPT guid=$guid (profile not found)")
            return
        }
        append(
            context,
            "CONNECT ATTEMPT name=\"${p.remarks}\" protocol=${p.configType} " +
                "server=${p.server}:${p.serverPort} network=${p.network} security=${p.security} " +
                "flow=${p.flow} sni=${p.sni} host=${p.host} path=${p.path}",
        )
    }

    /** Called when the engine reports the connection failed, with its exact error text. */
    fun logConnectFailure(context: Context, error: String) {
        append(context, "CONNECT FAILED: $error")
    }

    fun logConnectSuccess(context: Context) {
        append(context, "CONNECT SUCCEEDED")
    }

    /** Opens the share sheet so the person can send the log via any app (Telegram, etc.). */
    fun shareLogFile(context: Context) {
        val file = logFile(context)
        if (!file.exists() || file.length() == 0L) {
            file.writeText("(no log entries yet)\n")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "ارسال گزارش خطا").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /** Wipes the log — handy before reproducing an issue so the file only has the relevant attempt. */
    fun clear(context: Context) {
        try {
            logFile(context).writeText("")
        } catch (e: Exception) {
        }
    }
}
