package ir.onespeed.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.onespeed.app.data.ConnectionManager
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
    var servers by remember { mutableStateOf(listOf<ServerInfo>()) }
    var autoBest by remember { mutableStateOf(true) }
    var selectedGuid by remember { mutableStateOf<String?>(null) }
    var currentLocationName by remember { mutableStateOf("بهترین سرور (خودکار)") }
    var sheetOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        servers = ConnectionManager.listServers()
        onRefreshSub()
        servers = ConnectionManager.listServers()
    }

    Column(Modifier.fillMaxSize().padding(18.dp)) {

        // ---- top bar ----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconChip(icon = { Text("✈", fontSize = 15.sp) }, onClick = { /* TODO: open telegram channel */ })
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "OneSpeed",
                    style = TextStyle(brush = AppColors.brandGradient, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                )
                Text("SECURE · FAST · PRIVATE", fontSize = 8.sp, color = AppColors.muted2)
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
                    DropdownMenuItem(text = { Text("خروج از سرویس", color = AppColors.red, fontWeight = FontWeight.Bold) }, onClick = { menuOpen = false; onLogout() })
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- connect button ----
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (vpnState) {
                    VpnState.IDLE -> "برای اتصال ضربه بزنید"
                    VpnState.CONNECTING -> "در حال اتصال..."
                    VpnState.CONNECTED -> "متصل شد"
                },
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = if (vpnState == VpnState.CONNECTED) AppColors.aqua else AppColors.muted,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.size(100.dp)
                    .background(
                        if (vpnState == VpnState.CONNECTED) AppColors.brandGradient else androidx.compose.ui.graphics.SolidColor(AppColors.surface),
                        shape = CircleShape,
                    )
                    .clickableRipple {
                        if (vpnState != VpnState.IDLE) {
                            onDisconnect()
                        } else if (servers.isNotEmpty()) {
                            val target = if (autoBest) servers.first().guid else (selectedGuid ?: servers.first().guid)
                            onConnectRequest(target)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PowerSettingsNew, null,
                    tint = if (vpnState == VpnState.CONNECTED) Color.White else AppColors.muted,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // ---- stats grid ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(Modifier.weight(1f), "زمان", plan.daysText ?: "—")
            StatChip(Modifier.weight(1f), "حجم", plan.volumeText ?: "—")
        }

        Spacer(Modifier.height(20.dp))

        // ---- location row ----
        Row(
            Modifier.fillMaxWidth()
                .background(AppColors.surface, RoundedCornerShape(14.dp))
                .clickableRipple { sheetOpen = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(currentLocationName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.text, modifier = Modifier.weight(1f))
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
                    Text("انتخاب لوکیشن", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    // Manual ping trigger, right next to the close button — pings
                    // every listed server immediately via the real engine (socketConnectTime).
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
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AppColors.blue)
                            } else {
                                Icon(Icons.Filled.Refresh, "بررسی پینگ", tint = AppColors.blue, modifier = Modifier.size(16.dp))
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
                        .background(AppColors.blue.copy(alpha = .07f), RoundedCornerShape(13.dp))
                        .clickableRipple { autoBest = true; selectedGuid = null }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("اتصال به بهترین سرور", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("انتخاب خودکار بر اساس کمترین پینگ", fontSize = 9.sp, color = AppColors.aqua, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(10.dp))

                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(servers, key = { it.guid }) { s ->
                        val isSel = !autoBest && selectedGuid == s.guid
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (isSel) AppColors.blue.copy(alpha = .05f) else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickableRipple { autoBest = false; selectedGuid = s.guid }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(s.name, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            PingBadge(s)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        currentLocationName = if (autoBest) "بهترین سرور (خودکار)" else (servers.find { it.guid == selectedGuid }?.name ?: "بهترین سرور (خودکار)")
                        sheetOpen = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
                ) { Text("تغییر لوکیشن", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PingBadge(s: ServerInfo) {
    when {
        s.pinging -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AppColors.blue)
        s.pingMs == null -> Badge(containerColor = AppColors.surface2) { Text("--", fontSize = 8.5.sp, color = AppColors.muted2) }
        else -> {
            val ms = s.pingMs!!
            val color = if (ms < 150) AppColors.aqua else AppColors.amber
            Badge(containerColor = color.copy(alpha = .12f)) { Text("${ms}ms", fontSize = 8.5.sp, color = color, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun StatChip(modifier: Modifier, label: String, value: String) {
    Column(
        modifier.background(AppColors.surface, RoundedCornerShape(13.dp)).padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = AppColors.muted)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IconChip(icon: @Composable () -> Unit, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).background(AppColors.surface, RoundedCornerShape(12.dp)).clickableRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun SmallIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(26.dp).background(AppColors.surface2, RoundedCornerShape(8.dp)).clickableRipple(onClick),
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
        Text(v, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
    }
}

private fun Modifier.clickableRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(onClick = onClick)
)
