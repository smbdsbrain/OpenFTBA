import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.androidLibrary)
}

// Show test stdout and forward an optional real-track folder to the smoke test:
//   gradle :shared:jvmTest -Ptracks="C:/path/to/sample-tracks"
tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
    }
    (project.findProperty("tracks") as String?)?.let { environment("OPENFTBA_TRACKS", it) }
    (project.findProperty("dem") as String?)?.let { environment("OPENFTBA_DEM", it) }
}

kotlin {
    jvm()
    // Wave 6: wasmJs for the Compose web client. commonMain is pure Kotlin so it ports
    // cleanly; the KMZ/KML reader + DEM stay in jvmMain (server/desktop only).
    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    // Wave 6: Android. commonMain (domain/analytics/api) compiles here; the Android
    // KMZ/KML parser (XmlPullParser, no StAX) lives in composeApp/androidMain.
    androidTarget()

    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "io.openftba.shared"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }
}
