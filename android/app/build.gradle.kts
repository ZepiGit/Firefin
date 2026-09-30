import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(environmentName: String, propertyName: String): String? =
    System.getenv(environmentName)?.takeIf { it.isNotBlank() }
        ?: (keystoreProperties[propertyName] as String?)?.takeIf { it.isNotBlank() }

val releaseKeystorePath = signingValue("MOONFIN_KEYSTORE_FILE", "storeFile")
val releaseStorePassword = signingValue("MOONFIN_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("MOONFIN_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("MOONFIN_KEY_PASSWORD", "keyPassword")

android {
    namespace = "org.moonfin.androidtv"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "27.0.12077973"

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    defaultConfig {
        applicationId = "org.moonfin.firetv32"
        minSdk = 21
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = "${flutter.versionName}-firetv32-r21"

        ndk {
            abiFilters += listOf("armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            if (
                releaseKeystorePath == null ||
                releaseStorePassword == null ||
                releaseKeyAlias == null ||
                releaseKeyPassword == null
            ) {
                throw GradleException(
                    "Release signing is not configured. Set MOONFIN_KEYSTORE_FILE, " +
                        "MOONFIN_KEYSTORE_PASSWORD, MOONFIN_KEY_ALIAS and " +
                        "MOONFIN_KEY_PASSWORD, or create a private android/keystore.properties file.",
                )
            }
            storeFile = file(releaseKeystorePath)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            excludes += setOf(
                "**/arm64-v8a/*.so",
                "**/x86/*.so",
                "**/x86_64/*.so",
            )
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}

// Release lint is run separately when its toolchain is available. The Fire TV
// APK build must also remain reproducible in an offline environment, where AGP
// otherwise attempts to download lint-gradle during assembleRelease.
tasks.configureEach {
    if (name.startsWith("lintVital")) {
        enabled = false
    }
}
