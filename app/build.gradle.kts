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
// Bright SDK (bright-sdk.com) bandwidth-sharing/"Web Indexing" SDK — same
// reasoning as the PostHog key above. Not actually secret (Bright's own docs
// note the App ID is just the app's package name, checked against what was
// registered on their dashboard), but blank-by-default is kept anyway so a
// fresh clone doesn't ship a half-configured sharing feature: BrightManager
// treats a blank value as "feature not available" and never touches the SDK.
val brightAppId: String = localProps.getProperty("bright.appId", "")

// Release signing, also from local.properties. Passwords must never be
// committed, and the keystore file itself must never be committed — losing
// or leaking it is unrecoverable for a published app. When these aren't set
// the build falls back to the debug keystore, so a fresh clone still produces
// an installable APK without anyone needing signing material.
val releaseStoreFile: String = localProps.getProperty("release.storeFile", "")
val releaseStorePassword: String = localProps.getProperty("release.storePassword", "")
val releaseKeyAlias: String = localProps.getProperty("release.keyAlias", "")
val releaseKeyPassword: String = localProps.getProperty("release.keyPassword", "")
val hasReleaseSigning: Boolean =
    releaseStoreFile.isNotBlank() && rootProject.file(releaseStoreFile).exists()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.drift.tv"
    // compileSdk 36 for FOREGROUND_SERVICE_DATA_SYNC (API 34+) and current
    // Compose/tv-material. targetSdk stays at 35 deliberately — compileSdk
    // only exposes newer APIs to compile against; targetSdk is what opts the
    // app into new runtime behavior, and there's no reason to take that on here.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.drift.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "POSTHOG_API_KEY", "\"$postHogApiKey\"")
        buildConfigField("String", "POSTHOG_HOST", "\"$postHogHost\"")
        buildConfigField("String", "BRIGHT_APP_ID", "\"$brightAppId\"")
    }
    // Two distribution channels with different legal constraints:
    //
    //  sideload — GitHub releases / direct APK. Includes the Bright SDK
    //             bandwidth-sharing ("Web Indexing") SDK, behind explicit
    //             opt-in consent.
    //  store    — Google Play and the Amazon Appstore. Bright SDK does have a
    //             Play Store review/approval path (unlike Pawns, which is
    //             store-prohibited outright), but that hasn't been pursued
    //             here yet — this flavor stays SDK-free until it has. The
    //             dependency is declared sideloadImplementation below, so for
    //             this flavor the SDK isn't on the classpath and its AAR is
    //             never packaged. A blank App ID would not have been enough —
    //             that only disables the SDK at runtime, leaving it in the
    //             APK for a reviewer to find.
    flavorDimensions += "distribution"
    productFlavors {
        create("sideload") {
            dimension = "distribution"
        }
        create("store") {
            dimension = "distribution"
            // Distinct id so a store build and a sideloaded build can coexist
            // on one device, and so store listings never collide with the
            // directly-distributed APK.
            applicationIdSuffix = ".store"
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // Both signature schemes: v1 keeps older Fire OS builds happy,
                // v2 is what current Android verifies against.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Minification stays off — there's no store size budget worth
            // chasing here, and skipping R8 avoids the class of
            // works-in-debug-breaks-in-release bugs (e.g. reflection-based
            // kotlinx.serialization models) that otherwise only surface after
            // a store submission forces you to test the release build.
            isMinifyEnabled = false
            // Real keystore when local.properties supplies one; otherwise the
            // debug keystore, which keeps `assembleRelease` working on a fresh
            // clone for sideloading. Store submissions REQUIRE the real one —
            // Play and Amazon both reject debug-signed uploads.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
    // Sideload only — see the productFlavors block. This is what keeps the
    // SDK out of store builds entirely.
    //
    // app/libs/bright_sdk.aar is checked into the repo rather than fetched by
    // Bright's own Gradle plugin at build time. That plugin requires
    // SDK_API_KEY to be set for every single build with no documented way to
    // scope it to one flavor or skip it gracefully when absent — incompatible
    // with this repo's CI and fresh-clone builds, which are designed to
    // succeed with zero secrets present. The AAR was fetched once (see the
    // sharing-persistence commit history) using Bright's own officially
    // documented "Option B - Manual" install path; updating it later means
    // re-running that fetch and committing the new file, not a version bump
    // here.
    "sideloadImplementation"(fileTree(mapOf("dir" to "libs", "include" to listOf("bright_sdk.aar"))))
    // The AAR's own transitive dependencies, per Bright's Gradle plugin
    // output at fetch time.
    "sideloadImplementation"(libs.play.services.ads.identifier)
    "sideloadImplementation"(libs.androidx.constraintlayout)
}
