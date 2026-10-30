import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Scraper keys are never committed: they come from local.properties (git-ignored), a -P Gradle property or an
// environment variable, in that order, and default to "" (feature off). See SCRAPER.md.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(name: String): String =
    (localProps.getProperty(name) ?: (project.findProperty(name) as String?) ?: System.getenv(name) ?: "")
        .replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.desikitchen.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.desikitchen.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "1.2.0"
        vectorDrawables { useSupportLibrary = true }
        // Phone builds: 64-bit ARM only (keeps the APK small; no emulator ABIs).
        ndk { abiFilters += "arm64-v8a" }

        buildConfigField("String", "CREATORS_API_URL", "\"${secret("CREATORS_API_URL")}\"")
        buildConfigField("String", "CREATORS_API_KEY", "\"${secret("CREATORS_API_KEY")}\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"${secret("OPENROUTER_API_KEY")}\"")
        buildConfigField("String", "OPENROUTER_MODEL", "\"${secret("OPENROUTER_MODEL").ifBlank { "google/gemini-2.5-flash" }}\"")
        // 1.1.3 cascade: Stage 3 cheapest text model on top comments; Stage 4 keyframes + audio model.
        buildConfigField("String", "OPENROUTER_TEXT_MODEL", "\"${secret("OPENROUTER_TEXT_MODEL").ifBlank { "mistralai/mistral-nemo" }}\"")
        buildConfigField("String", "OPENROUTER_STILLS_MODEL", "\"${secret("OPENROUTER_STILLS_MODEL").ifBlank { "google/gemini-2.5-flash-lite" }}\"")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Screenshot tour (Robolectric + Roborazzi) only runs with -Pscreens; see UiTourScreenshots.
                it.systemProperty("dk.screens", (project.findProperty("screens") ?: "").toString())
                it.systemProperty("roborazzi.test.record", "true")
                it.maxHeapSize = "3g"
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Room migration tests read the exported schema JSON.
android.sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsoup)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    // Real org.json for JVM unit tests (android.jar only ships stubs).
    testImplementation(libs.org.json)
    // UI screenshot tour vs the v1 mocks (JVM only; nothing ships in the APK).
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation(libs.androidx.room.testing)
}
