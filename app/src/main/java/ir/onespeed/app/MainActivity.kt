package ir.onespeed.app

import android.content.BroadcastReceiver
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch

sealed class Screen { data object Splash : Screen(); data object Login : Screen(); data object Dashboard : Screen() }

class MainActivity : ComponentActivity() {
    private var statusReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var screen by remember { mutableStateOf<Screen>(Screen.Splash) }
            var token by remember { mutableStateOf<String?>(null) }
            var plan by remember { mutableStateOf(PlanInfo()) }
            var vpnState by remember { mutableStateOf(VpnState.IDLE) }
            var errorMsg by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            val vpnPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { /* result observed via next connect() attempt */ }

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
                    screen = Screen.Dashboard
                } else {
                    screen = Screen.Login
                }
            }

            MaterialTheme {
                Box(Modifier.fillMaxSize().background(AppColors.bg)) {
                    when (val s = screen) {
                        Screen.Splash -> SplashScreen()
                        Screen.Login -> LoginScreen(
                            onSubmit = { inputToken ->
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
                                }
                            },
                            error = errorMsg,
                        )
                        Screen.Dashboard -> DashboardScreen(
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
                                scope.launch {
                                    val t = token ?: return@launch
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

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(84.dp)
                    .background(AppColors.brandGradient, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("⚡", fontSize = 32.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text("OneSpeed", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.blue)
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = AppColors.blue)
        }
    }
}

@Composable
private fun LoginScreen(onSubmit: (String) -> Unit, error: String?) {
    var text by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(76.dp).background(AppColors.surface, shape = androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("⚡", fontSize = 28.sp) }
        Spacer(Modifier.height(18.dp))
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
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { if (text.isNotBlank()) onSubmit(text.trim()) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
        ) { Text("ورود و دریافت اطلاعات", fontWeight = FontWeight.Bold) }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = AppColors.red, fontSize = 11.5.sp)
        }
    }
}
