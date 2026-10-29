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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tjdoolittle.mileagelog"
        minSdk = 26
        targetSdk = 35
        versionCode = ciBuild
        versionName = "1.1.$ciRun"
    }

    signingConfigs {
        // One fixed key in the repo so updates install over earlier builds without losing trips.
        // This is a sideload key for personal use. Replace it before publishing to the Play Store.
        create("sideload") {
            storeFile = file("mileage-log.jks")
            storePassword = "mileagelog"
            keyAlias = "mileage"
            keyPassword = "mileagelog"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
        debug {
            signingConfig = signingConfigs.getByName("sideload")
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
