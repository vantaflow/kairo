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
    namespace = "com.tvhome.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tvhome.launcher"
        minSdk = 30
        targetSdk = 34
        versionCode = 10202
        versionName = "1.2.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PIXABAY_API_KEY", "\"${secrets.getProperty("PIXABAY_API_KEY", "")}\"")
    }

    // Clé dédiée (hors git, sauvegardée par l'utilisateur) : debug (ADB) et release (mises à jour)
    // sont signés pareil, sinon la mise à jour ne s'installe pas par-dessus l'APK installé par ADB.
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
        debug {
            signingConfig = signing
        }
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.tv.material)
    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.tvprovider)
    implementation(libs.coroutines.android)
    // Installe les profils de démarrage de Compose (APK installé hors Play Store) : rendu bien plus fluide.
    implementation(libs.profileinstaller)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
