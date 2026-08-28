package ir.onespeed.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.onespeed.app.data.ConnectionManager
import ir.onespeed.app.data.IpLookup
import ir.onespeed.app.data.PlanInfo
import ir.onespeed.app.data.ServerInfo
import ir.onespeed.app.data.VpnState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    plan: PlanInfo,
    vpnState: VpnState,
    onConnectRequest: (guid: String) -> Unit,
    onDisconnect: () -> Unit,
    onLogout: () -> Unit,
    onRefreshSub: suspend () -> Unit,
) {
    val context = LocalContext.current
    var servers by remember { mutableStateOf(listOf<ServerInfo>()) }
    var autoBest by remember { mutableStateOf(true) }
    var selectedGuid by remember { mutableStateOf<String?>(null) }
    var currentLocationName by remember { mutableStateOf("بهترین سرور (خودکار)") }
    var sheetOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var connectedIp by remember { mutableStateOf<String?>(null) }
    var seconds by remember { mutableStateOf(0) }
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

    // Real exit IP, fetched through the tunnel once connected (not faked).
    LaunchedEffect(vpnState) {
        if (vpnState == VpnState.CONNECTED) {
            connectedIp = null
            connectedIp = IpLookup.currentIp()
        } else {
            connectedIp = null
        }
    }

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
                    } else if (servers.isNotEmpty()) {
                        val target = if (autoBest) servers.first().guid else (selectedGuid ?: servers.first().guid)
                        onConnectRequest(target)
                    }
                },
            )
        }

        Spacer(Modifier.height(18.dp))

        // ---- stats grid: 2x2, matches the mockup's four equal chips ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(Modifier.weight(1f), "دانلود", "—", isSpeed = true)
            StatChip(Modifier.weight(1f), "آپلود", "—", isSpeed = true)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(Modifier.weight(1f), "زمان", plan.daysText?.let { if (it == "∞") it else "$it روز" } ?: "—")
            StatChip(Modifier.weight(1f), "حجم", plan.volumeText?.let { if (it == "∞") "نامحدود" else "$it GB" } ?: "—")
        }

        Spacer(Modifier.height(12.dp))

        if (vpnState == VpnState.CONNECTED) {
            Row(
                Modifier.fillMaxWidth()
                    .background(AppColors.surface2, RoundedCornerShape(10.dp))
                    .border(1.dp, AppColors.line, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).background(AppColors.thyme, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    connectedIp ?: "در حال دریافت آی‌پی...",
                    fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = AppColors.text,
                )
                Spacer(Modifier.width(6.dp))
                Text("· آی‌پی متصل", fontSize = 8.5.sp, color = AppColors.muted)
            }
            Spacer(Modifier.height(10.dp))
        }

        // ---- location row ----
        Row(
            Modifier.fillMaxWidth()
                .background(AppColors.surface, RoundedCornerShape(14.dp))
                .border(1.dp, AppColors.line, RoundedCornerShape(14.dp))
                .clickable { sheetOpen = true }
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

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallIconButton(
                            onClick = {
                                if (pingingAll) return@SmallIconButton
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
                            },
                        ) {
                            if (pingingAll) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AppColors.sky)
                            } else {
                                Icon(Icons.Filled.Refresh, "بررسی پینگ", tint = AppColors.sky, modifier = Modifier.size(16.dp))
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
                        .clickable { autoBest = true; selectedGuid = null }
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
                                .clickable {
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
                .clickable(onClick = onClick),
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
private fun StatChip(modifier: Modifier, label: String, value: String, isSpeed: Boolean = false) {
    Column(
        modifier
            .background(AppColors.surface, RoundedCornerShape(14.dp))
            .border(1.dp, AppColors.line, RoundedCornerShape(14.dp))
            .padding(vertical = 8.dp, horizontal = 10.dp)
            .height(64.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AppColors.muted)
        Text(
            buildString { append(value); if (isSpeed) append(" MB/s") },
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.text,
        )
    }
}

@Composable
private fun IconChip(icon: @Composable () -> Unit, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp)
            .background(AppColors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun SmallIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(26.dp).background(AppColors.surface2, RoundedCornerShape(8.dp)).clickable(onClick = onClick),
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
