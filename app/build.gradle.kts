import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// PostHog credentials live in local.properties (gitignored, same as sdk.dir)
// rather than in source — not because a project API key is secret (it's
// designed to be embedded in client apps, same threat model as a GA
// measurement ID), but so a fresh clone builds with analytics simply off
// instead of every contributor needing to edit tracked source to build.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val postHogApiKey: String = localProps.getProperty("posthog.apiKey", "")
val postHogHost: String = localProps.getProperty("posthog.host", "https://us.i.posthog.com")
// Pawns.app bandwidth-sharing SDK — same reasoning as the PostHog key above.
// Blank by default: PawnsManager treats a blank key as "feature not
// available" and never shows the consent screen at all, so a fresh clone
// doesn't ship a half-configured sharing feature.
val pawnsApiKey: String = localProps.getProperty("pawns.apiKey", "")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.drift.tv"
    // The Pawns SDK's own dependencies (androidx.core 1.17.0) require
    // compileSdk 36+. targetSdk stays at 35 deliberately — compileSdk only
    // exposes newer APIs to compile against; targetSdk is what opts the app
    // into new runtime behavior, and there's no reason to take that on here.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.drift.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "POSTHOG_API_KEY", "\"$postHogApiKey\"")
        buildConfigField("String", "POSTHOG_HOST", "\"$postHogHost\"")
        buildConfigField("String", "PAWNS_API_KEY", "\"$pawnsApiKey\"")
    }
    buildTypes {
        release {
            // Sideload-only: reuse the auto-generated debug keystore so
            // `assembleRelease` produces a directly-installable APK with no
            // manual signing step. Minification is off too — there's no store
            // size budget to chase, and skipping R8 avoids the class of
            // works-in-debug-breaks-in-release bugs (e.g. reflection-based
            // kotlinx.serialization models) that normally only show up after
            // a store submission forces you to test the release build.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.posthog.android)
    implementation(libs.pawns.sdk)
}
