package ir.onespeed.app.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.v2ray.ang.R

/**
 * Vazirmatn, matching the approved HTML mockup's `font-family:'Vazirmatn'`.
 * The .ttf files themselves aren't checked into the repo (fonts are binary
 * and this project is edited/zipped by hand) — the build workflow downloads
 * them into res/font/ before compiling, the same way it fetches libv2ray.aar.
 * If a local (non-CI) build ever fails on "resource font/vazir_regular not
 * found", run the curl commands from build-apk.yml's font step manually.
 */
val VazirmatnFamily = FontFamily(
    Font(R.font.vazir_regular, FontWeight.Normal),
    Font(R.font.vazir_medium, FontWeight.Medium),
    Font(R.font.vazir_semibold, FontWeight.SemiBold),
    Font(R.font.vazir_bold, FontWeight.Bold),
    Font(R.font.vazir_extrabold, FontWeight.ExtraBold),
)
