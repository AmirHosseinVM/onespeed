package com.v2ray.ang

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.dto.entities.SubscriptionItem
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class AngApplication : Application() {
    companion object {
        lateinit var application: AngApplication
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base?.let(ContextCompat::getContextForLanguage))
        application = this
    }

    private fun writeCrashLog(t: Throwable) {
        try {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            val text = sw.toString()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "onespeed_crash.txt")
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { os ->
                        os.write(text.toByteArray())
                    }
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, "onespeed_crash.txt")
                file.writeText(text)
            }

            val internalFile = File(cacheDir, "onespeed_crash.txt")
            internalFile.writeText(text)
        } catch (e: Exception) {
        }
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrashLog(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            MmkvManager.initialize(this)

            if (MmkvManager.decodeSubscription(AppConfig.DICODE_PRIMARY_SUBSCRIPTION_ID) == null) {
                MmkvManager.encodeSubscription(
                    AppConfig.DICODE_PRIMARY_SUBSCRIPTION_ID,
                    SubscriptionItem(
                        remarks = "Dicode Config Checker",
                        url = AppConfig.DICODE_PRIMARY_SUBSCRIPTION_URL,
                        enabled = true,
                        autoUpdate = true,
                        updateInterval = 60,
                    ),
                )
            }

            AppLocaleManager.initialize(this)

            WorkManager.initialize(this, workManagerConfiguration)

            SettingsManager.initApp(this)
        } catch (t: Throwable) {
            writeCrashLog(t)
            throw t
        }
    }
}
