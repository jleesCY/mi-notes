import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.jlees.minotes"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jlees.minotes"
        minSdk = 26
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        // installs next to the release app: separate package, name and data
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
        release {
            // 64-bit phones only ("mi-notes-<version>-release.apk", as mi. Browser's): ML Kit's text recognizer (scan/)
            // brings native code per CPU type, and 32-bit phones are old budget models; x86 is emulators
            ndk { abiFilters += listOf("arm64-v8a") }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // ML Kit's native code (scan/) compressed inside the APK: about half the download. Android unpacks the
    // phone's own copy at install, so the space it takes on the phone is about the same either way
    packaging { jniLibs { useLegacyPackaging = true } }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Robolectric tests (Android code in plain unit tests) read the app's resources
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Robolectric reaches into the JDK's internals, which newer JDKs close off unless asked
            all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED") }
        }
    }
}

base.archivesName.set("mi-notes-${android.defaultConfig.versionName}")

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("com.materialkolor:material-kolor:5.0.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    // scanning text (scan/): on-device recognition, its model bundled in the APK; its reporting is removed in the manifest
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // finds leaked screens and views while developing; debug builds only, nothing of it reaches a release
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
}
