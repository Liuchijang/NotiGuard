import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from local.properties (never committed):
//   release.storeFile / release.storePassword / release.keyAlias / release.keyPassword
// Without them the release build is produced unsigned.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.local.notiguard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.local.notiguard"
        minSdk = 26
        // targetSdk 22 is intentional and must not be raised (same trick as FCMGuard-HyperOS):
        // legacy-target apps get WRITE_SETTINGS at install and may write the *non-public*
        // Settings.System key MILLET_NO_RESTRICT_APP, so FCM Guard works without Shizuku.
        // Stock Android 14+ blocks installing target < 23 (adb install --bypass-low-target-sdk-block);
        // Xiaomi's China ROM installs it directly.
        @Suppress("ExpiredTargetSdkVersion")
        targetSdk = 22
        versionCode = 3
        versionName = "2.1"
    }

    lint {
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
        checkReleaseBuilds = false
    }

    signingConfigs {
        localProps.getProperty("release.storeFile")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            vcsInfo.include = false
            signingConfig = signingConfigs.findByName("release")
            // No R8: Shizuku binds the UserService by class name, and the APK is small anyway.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Keep build-machine details out of the APK: no VCS info file, no dependency metadata block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Shizuku: run privileged shell commands without root.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    implementation(project(":fcmcore"))
}
