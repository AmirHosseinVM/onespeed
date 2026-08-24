plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.v2ray.ang"          // matches the vendored engine's package — do not change
    compileSdk = 36

    defaultConfig {
        applicationId = "ir.onespeed.app" // our real published app id
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        multiDexEnabled = true
    }

    signingConfigs {
        create("release") {
            val hasKeystore = System.getenv("ONESPEED_KEYSTORE") != null
            if (hasKeystore) {
                storeFile = file(System.getenv("ONESPEED_KEYSTORE"))
                storePassword = System.getenv("ONESPEED_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ONESPEED_KEY_ALIAS") ?: "onespeed"
                keyPassword = System.getenv("ONESPEED_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // No keystore configured yet? Falls back to the debug key so you
            // still get an installable APK today.
            signingConfig = if (System.getenv("ONESPEED_KEYSTORE") != null)
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        resources.excludes.add("META-INF/**")
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.mmkv.static)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.work.runtime.ktx)
    implementation(libs.work.multiprocess)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
