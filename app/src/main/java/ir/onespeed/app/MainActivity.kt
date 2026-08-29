package ir.onespeed.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.onespeed.app.data.ApiResult
import ir.onespeed.app.data.ApiService
import ir.onespeed.app.data.ConnectionManager
import ir.onespeed.app.data.DeviceIdService
import ir.onespeed.app.data.PlanInfo
import ir.onespeed.app.data.VpnState
import ir.onespeed.app.ui.AppColors
import ir.onespeed.app.ui.DashboardScreen
import ir.onespeed.app.ui.ThemeState
import ir.onespeed.app.ui.VazirmatnFamily
import kotlinx.coroutines.launch

sealed class Screen { data object Splash : Screen(); data object Login : Screen(); data object Dashboard : Screen() }

class MainActivity : ComponentActivity() {
    private var statusReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            remember { ThemeState.init(this@MainActivity) }

            var screen by remember { mutableStateOf<Screen>(Screen.Splash) }
            var token by remember { mutableStateOf<String?>(null) }
            var plan by remember { mutableStateOf(PlanInfo()) }
            var vpnState by remember { mutableStateOf(VpnState.IDLE) }
            var errorMsg by remember { mutableStateOf<String?>(null) }
            var loginLoading by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            val vpnPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { /* result observed via next connect() attempt */ }

            // Android 13+ (API 33) requires this to be granted at RUNTIME, not just
            // declared in the manifest — without it, startForeground() still runs the
            // VPN service but the notification never actually posts, so the user never
            // sees "متصل شد" in the notification shade even though the tunnel is up.
            val notifPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* granted or denied — either way, proceed; VPN still works without it */ }

            fun ensureNotificationPermission() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        this, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            fun requestVpnPermissionThenConnect(guid: String) {
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    vpnPermissionLauncher.launch(intent)
                } else {
                    ConnectionManager.connect(this, guid)
                }
            }

            DisposableEffect(Unit) {
                val receiver = ConnectionManager.registerStatusReceiver(this@MainActivity) { state, err ->
                    vpnState = state
                    if (err != null) errorMsg = err
                }
                statusReceiver = receiver
                onDispose { unregisterReceiver(receiver) }
            }

            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(1200)
                val saved = DeviceIdService.getSavedToken(this@MainActivity)
                if (saved != null) {
                    token = saved
                    // Returning user: re-fetch and re-import before showing the
                    // dashboard, otherwise the server list can be stale/out of
                    // sync with what's actually stored, causing "invalid config"
                    // on connect. Falls back to whatever is already cached if
                    // the network call fails, so the app still opens offline.
                    val deviceId = DeviceIdService.getDeviceId(this@MainActivity)
                    when (val result = ApiService.fetchSub(deviceId, saved)) {
                        is ApiResult.Success -> {
                            ConnectionManager.importSub(result.rawBody)
                            plan = result.plan
                        }
                        else -> { /* keep whatever was cached locally; Dashboard's refresh will retry */ }
                    }
                    screen = Screen.Dashboard
                } else {
                    screen = Screen.Login
                }
            }

            MaterialTheme(
                typography = Typography(
                    bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = VazirmatnFamily),
                    bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = VazirmatnFamily),
                    bodySmall = MaterialTheme.typography.bodySmall.copy(fontFamily = VazirmatnFamily),
                    titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = VazirmatnFamily, fontWeight = FontWeight.Bold),
                    titleMedium = MaterialTheme.typography.titleMedium.copy(fontFamily = VazirmatnFamily, fontWeight = FontWeight.Bold),
                    labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = VazirmatnFamily, fontWeight = FontWeight.Bold),
                ),
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.fillMaxSize().background(AppColors.bg)) {
                    when (val s = screen) {
                        Screen.Splash -> SplashScreen()
                        Screen.Login -> LoginScreen(
                            loading = loginLoading,
                            onSubmit = { inputToken ->
                                loginLoading = true
                                errorMsg = null
                                scope.launch {
                                    val deviceId = DeviceIdService.getDeviceId(this@MainActivity)
                                    when (val result = ApiService.fetchSub(deviceId, inputToken)) {
                                        is ApiResult.Success -> {
                                            ConnectionManager.importSub(result.rawBody)
                                            DeviceIdService.saveToken(this@MainActivity, inputToken)
                                            token = inputToken
                                            plan = result.plan
                                            errorMsg = null
                                            screen = Screen.Dashboard
                                        }
                                        is ApiResult.DeviceLimit -> errorMsg =
                                            "این اشتراک روی حداکثر تعداد دستگاه مجاز (${result.limit ?: "?"}) فعال است"
                                        is ApiResult.Error -> errorMsg = "دریافت اطلاعات ناموفق بود، دوباره تلاش کنید"
                                    }
                                    loginLoading = false
                                }
                            },
                            error = errorMsg,
                        )
                        Screen.Dashboard -> {
                            LaunchedEffect(Unit) { ensureNotificationPermission() }
                            DashboardScreen(
                            plan = plan,
                            vpnState = vpnState,
                            onConnectRequest = { guid -> requestVpnPermissionThenConnect(guid) },
                            onDisconnect = { ConnectionManager.disconnect(this@MainActivity) },
                            onLogout = {
                                ConnectionManager.disconnect(this@MainActivity)
                                DeviceIdService.clearToken(this@MainActivity)
                                token = null
                                screen = Screen.Login
                            },
                            onRefreshSub = {
                                // Suspend function, invoked from inside DashboardScreen's own
                                // LaunchedEffect coroutine, so the server list it reads right
                                // after is guaranteed to reflect this fetch — not stale data.
                                val t = token
                                if (t != null) {
                                    val deviceId = DeviceIdService.getDeviceId(this@MainActivity)
                                    val result = ApiService.fetchSub(deviceId, t)
                                    if (result is ApiResult.Success) {
                                        ConnectionManager.importSub(result.rawBody)
                                        plan = result.plan
                                    }
                                }
                            },
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "OneSpeed",
            style = TextStyle(brush = AppColors.brandGradient, fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun LoginScreen(loading: Boolean, onSubmit: (String) -> Unit, error: String?) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ورود به سرویس", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "توکنی که از ربات تلگرام گرفتی رو اینجا بچسبون",
            fontSize = 12.sp, color = AppColors.muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            placeholder = { Text("لطفا توکن خود را وارد کنید", color = AppColors.muted2) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !loading,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.sky,
                unfocusedBorderColor = AppColors.line,
            ),
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { if (text.isNotBlank() && !loading) onSubmit(text.trim()) },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !loading,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.sky),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text("ورود و دریافت اطلاعات", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        TextButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/OneSpeedBot"))
                context.startActivity(intent)
            },
            enabled = !loading,
        ) {
            Text("اگر توکن ندارید اینجا کلیک کنید", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.sky)
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                error, color = AppColors.red, fontSize = 11.5.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .background(AppColors.red.copy(alpha = .1f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
            )
        }
    }
}
