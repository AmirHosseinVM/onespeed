package ir.onespeed.app.data

data class ServerInfo(
    val guid: String,       // MmkvManager server guid — used to select/connect
    val name: String,
    val flagCode: String?,  // ISO 3166-1 alpha-2, lowercase, or null
    var pingMs: Long? = null,
    var pinging: Boolean = false,
)

data class PlanInfo(
    val daysText: String? = null,
    val volumeText: String? = null,
    val expiryDate: String? = null,
    val planName: String = "OneSpeed",
)
