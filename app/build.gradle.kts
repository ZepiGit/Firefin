import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val productionSigning = providers.gradleProperty("firefin.productionSigning").orNull == "true"
fun requiredSigningValue(name: String): String = providers.environmentVariable(name).orNull
    ?.takeIf(String::isNotBlank) ?: throw GradleException("Missing required signing setting: $name")

android {
    namespace = "zepigit.firefin.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "zepigit.firefin.app"
        minSdk = 21
        targetSdk = 34
        versionCode = versionProperties.getProperty("versionCode").toInt()
        versionName = versionProperties.getProperty("versionName")
    }

    if (productionSigning) {
        signingConfigs.create("firefinProduction") {
            storeFile = file(requiredSigningValue("FIREFIN_KEYSTORE_FILE"))
            storePassword = requiredSigningValue("FIREFIN_KEYSTORE_PASSWORD")
            keyAlias = requiredSigningValue("FIREFIN_KEY_ALIAS")
            keyPassword = requiredSigningValue("FIREFIN_KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (productionSigning) signingConfig = signingConfigs.getByName("firefinProduction")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        xmlReport = true
        textReport = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    val media3 = "1.8.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.json:json:20231013")
}
