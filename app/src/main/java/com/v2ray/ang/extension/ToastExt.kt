package com.v2ray.ang.extension

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Simplified for OneSpeed: always uses a native Android Toast (the original
 * dicodePing/v2rayNG in-app Snackbar routing lived in its ui/ package, which
 * we intentionally did not vendor — OneSpeed has its own UI layer).
 */
fun Context.toast(message: Int) = showToast(getString(message))
fun Context.toast(message: CharSequence) = showToast(message)
fun Context.toastSuccess(message: Int) = showToast(getString(message))
fun Context.toastSuccess(message: CharSequence) = showToast(message)
fun Context.toastError(message: Int) = showToast(getString(message))
fun Context.toastError(message: CharSequence) = showToast(message)
fun Context.toastInfo(message: Int) = showToast(getString(message))
fun Context.toastInfo(message: CharSequence) = showToast(message)

private fun Context.showToast(message: CharSequence) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    } else {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
