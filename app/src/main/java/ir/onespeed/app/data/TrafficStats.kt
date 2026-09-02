package ir.onespeed.app.data

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager

/**
 * The actual VPN tunnel runs in a separate process (CoreVpnService, process
 * ":RunSoLibV2RayDaemon"), which is also what keeps the persistent notification's
 * traffic numbers updated. This reads the same MMKV-persisted totals from our
 * process so the dashboard can show the real in-use data amount — not speed,
 * the running total since this connection started — matching what the
 * notification already shows.
 */
object TrafficStats {
    /** Returns (uplinkBytes, downlinkBytes) accumulated since the current connection started. */
    fun currentTotals(): Pair<Long, Long> {
        val up = MmkvManager.decodeSettingsLong(AppConfig.CACHE_TOTAL_UPLINK, 0L)
        val down = MmkvManager.decodeSettingsLong(AppConfig.CACHE_TOTAL_DOWNLINK, 0L)
        return up to down
    }

    /** Formats bytes the way the approved UI mockup does: "1.53 مگابایت" / "483.48 کیلوبایت". */
    fun format(bytes: Long): String {
        if (bytes < 1024) return "$bytes بایت"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.2f کیلوبایت", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.2f مگابایت", mb)
        val gb = mb / 1024.0
        return String.format("%.2f گیگابایت", gb)
    }
}
