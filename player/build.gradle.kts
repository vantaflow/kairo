import java.util.Properties

val secrets = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vantaflow.kairo.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vantaflow.kairo.player"
        minSdk = 30
        targetSdk = 34
        versionCode = 11704
        versionName = "1.17.4"
    }

    // Même clé que Kairo (hors git) : les mises à jour s'installent par-dessus.
    val keystore = secrets.getProperty("TVHOME_KEYSTORE")?.let { rootProject.file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("tvhome") {
                storeFile = keystore
                storePassword = secrets.getProperty("TVHOME_KEYSTORE_PASSWORD")
                keyAlias = secrets.getProperty("TVHOME_KEY_ALIAS", "tvhome")
                keyPassword = secrets.getProperty("TVHOME_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        val signing = if (keystore != null) signingConfigs.getByName("tvhome") else signingConfigs.getByName("debug")
        debug { signingConfig = signing }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signing
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.tv.material)
    implementation(libs.coil.compose)
    implementation(libs.tvprovider)
    implementation(libs.coroutines.android)
    implementation(libs.profileinstaller)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    testImplementation(libs.junit)
}
