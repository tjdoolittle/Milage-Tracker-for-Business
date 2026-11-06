plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Version code comes from the CI run number so every build installs over the last one.
// +100 keeps it above the earlier builds made in another repo (up to 1.0.3), so updates install cleanly.
val ciRun = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val ciBuild = 100 + ciRun

android {
    namespace = "com.tjdoolittle.mileagelog"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tjdoolittle.mileagelog"
        minSdk = 26
        targetSdk = 36
        versionCode = ciBuild
        versionName = "1.1.$ciRun"
    }

    // The signing key is NOT in the repo. CI writes it from the ANDROID_KEYSTORE_BASE64 secret to
    // app/mileage-log.jks and passes its password in ANDROID_KEYSTORE_PASSWORD. Every build must use
    // this same key, or Android refuses to install the update over the app (and its trips).
    val keystore = file("mileage-log.jks")
    val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val canSign = keystore.exists() && !keystorePassword.isNullOrEmpty()

    signingConfigs {
        if (canSign) {
            create("sideload") {
                storeFile = keystore
                storePassword = keystorePassword
                keyAlias = "mileage"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinks and obfuscates the code (Play's code-optimization check). The mapping
            // file goes into the AAB automatically, so Play Console can still read crash reports.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Ship native debug symbols inside the AAB so Play Console can decode native crashes.
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            // Without the real key (forks, the screenshot job) sign with the debug key so the
            // release build can still be installed and smoke-tested.
            signingConfig = signingConfigs.getByName(if (canSign) "sideload" else "debug")
        }
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
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    // play-services-location pulls in fragment 1.1.0, which Google flags as outdated.
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
