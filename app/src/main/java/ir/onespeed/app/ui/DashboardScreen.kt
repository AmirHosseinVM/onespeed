package ir.onespeed.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.onespeed.app.data.ConnectionManager
import ir.onespeed.app.data.PlanInfo
import ir.onespeed.app.data.ServerInfo
import ir.onespeed.app.data.TrafficStats
import ir.onespeed.app.data.VpnState
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset

/** Tap feedback without the web-style ripple/highlight box — just fires onClick. */
@Composable
private fun Modifier.tapOnly(onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    plan: PlanInfo,
    vpnState: VpnState,
    connectError: String? = null,
    onConnectRequest: (guid: String) -> Unit,
    onDisconnect: () -> Unit,
    onSendLog: () -> Unit = {},
    onLogout: () -> Unit,
    onRefreshSub: suspend () -> Unit,
) {
    val context = LocalContext.current
    var servers by remember { mutableStateOf(listOf<ServerInfo>()) }
    var autoBest by remember { mutableStateOf(true) }
    var selectedGuid by remember { mutableStateOf<String?>(null) }
    var currentLocationName by remember { mutableStateOf("بهترین سرور (خودکار)") }
    var connectedServerAddress by remember { mutableStateOf<String?>(null) }
    var connectedServerIp by remember { mutableStateOf<String?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }
    var uplinkBytes by remember { mutableStateOf(0L) }
    var downlinkBytes by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        servers = ConnectionManager.listServers()
        onRefreshSub()
        servers = ConnectionManager.listServers()
    }

    // Timer while connected
    LaunchedEffect(vpnState) {
        if (vpnState == VpnState.CONNECTED) {
            seconds = 0
            while (true) {
                kotlinx.coroutines.delay(1000)
                seconds++
            }
        }
    }

    // Real cumulative data used this session — not speed, the running total,
    // same numbers already shown in the persistent notification. The VPN
    // tunnel runs in a separate process, so this polls the MMKV-persisted
    // totals that process writes every few seconds.
    LaunchedEffect(vpnState) {
        if (vpnState == VpnState.CONNECTED) {
            while (true) {
                val (up, down) = TrafficStats.currentTotals()
                uplinkBytes = up
                downlinkBytes = down
                kotlinx.coroutines.delay(1000)
            }
        } else {
            uplinkBytes = 0L
            downlinkBytes = 0L
        }
    }

    // Resolve the server's actual IP for display. The panel often gives a
    // domain (e.g. behind CDN/reality), and the person wants to see the real
    // server IP, not that domain and not the device's own public IP.
    LaunchedEffect(vpnState, connectedServerAddress) {
        if (vpnState == VpnState.CONNECTED && connectedServerAddress != null) {
            connectedServerIp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    java.net.InetAddress.getByName(connectedServerAddress).hostAddress
                } catch (e: Exception) {
                    connectedServerAddress
                }
            }
        } else {
            connectedServerIp = null
        }
    }

    fun resolveTargetGuid(): String? =
        if (autoBest) servers.firstOrNull()?.guid else (selectedGuid ?: servers.firstOrNull()?.guid)

    Box(Modifier.fillMaxSize()) {
        WorldMapBackground(
            Modifier.fillMaxSize(),
            serverFlags = servers.mapNotNull { it.flagCode },
        )

        Column(Modifier.fillMaxSize().padding(18.dp)) {

        // ---- top bar ----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                IconChip(icon = {
                    Icon(Icons.Filled.Send, "کانال تلگرام", tint = AppColors.sky, modifier = Modifier.size(16.dp))
                }, onClick = { /* TODO: wire real telegram channel URL */ })
                IconChip(icon = {
                    if (ThemeState.isDark) {
                        Icon(Icons.Filled.LightMode, "روشن", tint = AppColors.text, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Filled.DarkMode, "تاریک", tint = AppColors.text, modifier = Modifier.size(16.dp))
                    }
                }, onClick = { ThemeState.toggle(context) })
            }

            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "OneSpeed",
                    style = TextStyle(brush = AppColors.brandGradient, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                )
                Text("SECURE · FAST · PRIVATE", fontSize = 8.sp, color = AppColors.muted2, letterSpacing = 1.sp)
            }

            Box {
                IconChip(icon = { Icon(Icons.Filled.Person, null, tint = AppColors.muted, modifier = Modifier.size(17.dp)) },
                    onClick = { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    InfoRow("پلن", plan.planName)
                    InfoRow("روز باقی‌مانده", plan.daysText ?: "—")
                    InfoRow("حجم باقی‌مانده", plan.volumeText ?: "—")
                    InfoRow("تاریخ انقضا", plan.expiryDate ?: "—")
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("ارسال گزارش خطا", color = AppColors.sky, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Filled.Send, null, tint = AppColors.sky, modifier = Modifier.size(16.dp)) },
                        onClick = { menuOpen = false; onSendLog() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("خروج از سرویس", color = AppColors.red, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Filled.Logout, null, tint = AppColors.red, modifier = Modifier.size(16.dp)) },
                        onClick = { menuOpen = false; onLogout() },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- connect zone ----
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vpnState == VpnState.CONNECTING) {
                    CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 2.dp, color = AppColors.orange)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    when (vpnState) {
                        VpnState.IDLE -> "برای اتصال ضربه بزنید"
                        VpnState.CONNECTING -> "در حال اتصال..."
                        VpnState.CONNECTED -> "متصل شد"
                    },
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (vpnState == VpnState.CONNECTED) AppColors.thyme else AppColors.muted,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (vpnState == VpnState.CONNECTED) formatTimer(seconds) else "",
                fontSize = 9.sp, color = AppColors.muted2,
            )
            Spacer(Modifier.height(10.dp))

            ConnectButton(
                state = vpnState,
                onClick = {
                    if (vpnState != VpnState.IDLE) {
                        onDisconnect()
                    } else {
                        val targetGuid = resolveTargetGuid()
                        if (targetGuid != null) {
                            connectedServerAddress = servers.find { it.guid == targetGuid }?.address
                            onConnectRequest(targetGuid)
                        }
                    }
                },
            )

            if (vpnState == VpnState.IDLE && connectError != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .background(AppColors.red.copy(alpha = .1f), RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        connectError, color = AppColors.red, fontSize = 10.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    val clipboard = LocalClipboardManager.current
                    Box(
                        Modifier.background(AppColors.red.copy(alpha = .15f), RoundedCornerShape(8.dp))
                            .tapOnly { clipboard.setText(AnnotatedString(connectError)) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("کپی", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = AppColors.red)
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.background(AppColors.red.copy(alpha = .15f), RoundedCornerShape(8.dp))
                            .tapOnly(onSendLog)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("ارسال لاگ", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = AppColors.red)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- stats: باقی‌مانده / دانلود / آپلود در حال استفاده — یک ردیف با جداکننده ----
        Row(
            Modifier.fillMaxWidth()
                .background(AppColors.surface, RoundedCornerShape(14.dp))
                .border(1.dp, AppColors.line, RoundedCornerShape(14.dp))
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell(
                Modifier.weight(1f), Icons.Filled.CalendarMonth, AppColors.sky,
                "باقی‌مانده",
                plan.daysText?.let { if (it == "∞") it else "$it روز" } ?: "—",
            )
            VDivider()
            StatCell(
                Modifier.weight(1f), Icons.Filled.ArrowDownward, AppColors.sky,
                "دانلود", if (vpnState == VpnState.CONNECTED) TrafficStats.format(downlinkBytes) else "—",
            )
            VDivider()
            StatCell(
                Modifier.weight(1f), Icons.Filled.ArrowUpward, AppColors.thyme,
                "آپلود", if (vpnState == VpnState.CONNECTED) TrafficStats.format(uplinkBytes) else "—",
            )
        }

        Spacer(Modifier.height(12.dp))

        if (vpnState == VpnState.CONNECTED) {
            Row(
                Modifier.fillMaxWidth()
                    .background(AppColors.surface, RoundedCornerShape(10.dp))
                    .border(1.dp, AppColors.line, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Shield, null, tint = AppColors.thyme, modifier = Modifier.size(16.dp))
                Spacer(Modifier.weight(1f))
                Text("سرور متصل", fontSize = 9.sp, color = AppColors.muted)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(6.dp).background(AppColors.thyme, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    connectedServerIp ?: "در حال دریافت...",
                    fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = AppColors.text,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // ---- location row ----
        Row(
            Modifier.fillMaxWidth()
                .background(AppColors.surface, RoundedCornerShape(14.dp))
                .border(1.dp, AppColors.line, RoundedCornerShape(14.dp))
                .tapOnly { sheetOpen = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(currentLocationName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.text)
                Text("برای تغییر لوکیشن یا تست پینگ ضربه بزنید", fontSize = 8.5.sp, color = AppColors.thyme, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = AppColors.muted2, modifier = Modifier.size(16.dp))
        }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, containerColor = AppColors.surface) {
            var pingingAll by remember { mutableStateOf(false) }

            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("انتخاب لوکیشن", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.text)

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.background(AppColors.sky.copy(alpha = .1f), RoundedCornerShape(8.dp))
                                .tapOnly {
                                    if (pingingAll) return@tapOnly
                                    pingingAll = true
                                    servers.forEach { it.pinging = true }
                                    servers = servers.toList()
                                    scope.launch {
                                        val updated = servers.map { s ->
                                            val ms = ConnectionManager.ping(s.guid)
                                            s.copy(pingMs = ms, pinging = false)
                                        }
                                        servers = updated
                                        pingingAll = false
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (pingingAll) {
                                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = AppColors.sky)
                            } else {
                                Text("بررسی پینگ", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = AppColors.sky)
                            }
                        }
                        SmallIconButton(onClick = { sheetOpen = false }) {
                            Icon(Icons.Filled.Close, "بستن", tint = AppColors.muted, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier.fillMaxWidth()
                        .background(AppColors.sky.copy(alpha = .07f), RoundedCornerShape(13.dp))
                        .border(1.dp, AppColors.thyme.copy(alpha = .3f), RoundedCornerShape(13.dp))
                        .tapOnly { autoBest = true; selectedGuid = null }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("اتصال به بهترین سرور", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.text)
                        Text("انتخاب خودکار بر اساس کمترین پینگ واقعی", fontSize = 9.sp, color = AppColors.thyme, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(10.dp))

                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(servers, key = { it.guid }) { s ->
                        val isSel = !autoBest && selectedGuid == s.guid
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (isSel) AppColors.sky.copy(alpha = .1f) else Color.Transparent, RoundedCornerShape(12.dp))
                                .tapOnly {
                                    autoBest = false
                                    selectedGuid = s.guid
                                    currentLocationName = s.name
                                    sheetOpen = false
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(s.name, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = AppColors.text, modifier = Modifier.weight(1f))
                            PingBadge(s)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimer(totalSeconds: Int): String {
    val m = (totalSeconds / 60).toString().padStart(2, '0')
    val s = (totalSeconds % 60).toString().padStart(2, '0')
    return "$m:$s"
}

@Composable
private fun ConnectButton(state: VpnState, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.45f,
        animationSpec = infiniteRepeatable(animation = tween(1800, easing = LinearEasing)),
        label = "pulseScale",
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(1800, easing = LinearEasing)),
        label = "pulseAlpha",
    )

    Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
        // dashed-ish outer ring (solid thin ring — Compose has no native dashed circle without Canvas)
        Box(
            Modifier.size(102.dp)
                .border(1.5.dp, AppColors.line, CircleShape),
        )

        if (state == VpnState.CONNECTED) {
            Box(
                Modifier.size((95 * pulseScale).dp)
                    .alpha(pulseAlpha)
                    .border(2.dp, AppColors.thyme, CircleShape),
            )
        }

        Box(
            Modifier.size(95.dp)
                .background(
                    if (state == VpnState.CONNECTED) AppColors.brandGradient else SolidColor(AppColors.surface),
                    shape = CircleShape,
                )
                .border(1.dp, AppColors.line, CircleShape)
                .tapOnly(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PowerSettingsNew, null,
                tint = if (state == VpnState.CONNECTED) Color.White else AppColors.muted,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun PingBadge(s: ServerInfo) {
    when {
        s.pinging -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AppColors.sky)
        s.pingMs == null -> PingPill(AppColors.muted2.copy(alpha = .15f), AppColors.muted2, "--")
        else -> {
            val ms = s.pingMs!!
            val color = if (ms < 150) AppColors.thyme else AppColors.orange
            PingPill(color.copy(alpha = .15f), color, "${ms}ms")
        }
    }
}

@Composable
private fun PingPill(bg: Color, fg: Color, text: String) {
    Box(
        Modifier.background(bg, RoundedCornerShape(8.dp)).widthIn(min = 38.dp).padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 8.5.sp, color = fg, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatChip(modifier: Modifier, label: String, value: String, icon: ImageVector) {
    Column(
        modifier
            .background(AppColors.surface, RoundedCornerShape(14.dp))
            .border(1.dp, AppColors.line, RoundedCornerShape(14.dp))
            .padding(vertical = 8.dp, horizontal = 10.dp)
            .height(64.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AppColors.muted)
            Icon(icon, null, tint = AppColors.sky, modifier = Modifier.size(12.dp))
        }
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.text)
    }
}

@Composable
private fun IconChip(icon: @Composable () -> Unit, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp)
            .background(AppColors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.line, RoundedCornerShape(12.dp))
            .tapOnly(onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun SmallIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(26.dp).background(AppColors.surface2, RoundedCornerShape(8.dp)).tapOnly(onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).widthIn(min = 160.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(k, fontSize = 10.5.sp, color = AppColors.muted)
        Text(v, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = AppColors.text)
    }
}

@Composable
private fun StatCell(modifier: Modifier, icon: ImageVector, iconTint: Color, label: String, value: String) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AppColors.muted)
        }
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.text)
    }
}

@Composable
private fun VDivider() {
    Box(Modifier.width(1.dp).height(30.dp).background(AppColors.line))
}

/**
 * Rough dot-matrix world map, purely decorative like the approved mockup —
 * not geographically precise, just enough continent silhouette to read as a
 * map. Adds a highlighted dot for each server's country when we can place it.
 */
@Composable
private fun WorldMapBackground(modifier: Modifier, serverFlags: List<String>) {
    val dotColor = AppColors.line
    val markerColor = AppColors.sky
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Rough continent blobs as (centerXFrac, centerYFrac, radiusXFrac, radiusYFrac)
        val blobs = listOf(
            0.18f to 0.30f to (0.14f to 0.10f), // North America
            0.24f to 0.55f to (0.08f to 0.12f), // South America
            0.48f to 0.24f to (0.06f to 0.07f), // Europe
            0.50f to 0.45f to (0.08f to 0.14f), // Africa
            0.68f to 0.28f to (0.16f to 0.14f), // Asia
            0.82f to 0.62f to (0.07f to 0.06f), // Australia
        )
        val spacing = 14f
        var y = 0f
        while (y < h) {
            var x = 0f
            while (x < w) {
                val fx = x / w
                val fy = y / h
                val inside = blobs.any { (center, radius) ->
                    val (cx, cy) = center
                    val (rx, ry) = radius
                    val dx = (fx - cx) / rx
                    val dy = (fy - cy) / ry
                    dx * dx + dy * dy <= 1f
                }
                if (inside) {
                    drawCircle(dotColor, radius = 1.6f, center = Offset(x, y))
                }
                x += spacing
            }
            y += spacing
        }

        // Approximate marker positions for common country codes (fraction of width/height).
        val markerPos = mapOf(
            "us" to (0.16f to 0.30f), "ca" to (0.16f to 0.20f),
            "de" to (0.49f to 0.22f), "fr" to (0.46f to 0.24f), "gb" to (0.44f to 0.19f),
            "nl" to (0.47f to 0.21f), "ru" to (0.62f to 0.16f),
            "tr" to (0.54f to 0.27f), "ir" to (0.58f to 0.30f), "ae" to (0.60f to 0.34f),
            "in" to (0.66f to 0.38f), "cn" to (0.72f to 0.28f), "jp" to (0.80f to 0.28f),
            "au" to (0.82f to 0.62f), "br" to (0.26f to 0.55f),
        )
        serverFlags.distinct().forEach { code ->
            markerPos[code.lowercase()]?.let { (fx, fy) ->
                drawCircle(markerColor, radius = 4f, center = Offset(fx * w, fy * h))
            }
        }
    }
}
