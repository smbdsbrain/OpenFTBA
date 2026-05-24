import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidApplication)
}

kotlin {
    jvm("desktop")
    androidTarget()
    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig { outputFileName = "openftba.js" }
        }
        binaries.executable()
    }
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(projects.shared)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        val desktopTest by getting
        desktopTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
        val wasmJsMain by getting
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.browser)
        }
        val androidMain by getting
        androidMain.dependencies {
            implementation(compose.uiTooling)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.documentfile)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

android {
    namespace = "io.openftba"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig {
        applicationId = "io.openftba"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    // No INTERNET permission — privacy by default (see AndroidManifest).
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Forward an optional real-track folder to the desktop pipeline test:
//   gradle :composeApp:desktopTest -Ptracks="C:/path/to/sample-tracks"
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events(
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
        )
    }
    (project.findProperty("tracks") as String?)?.let { environment("OPENFTBA_TRACKS", it) }
}

compose.desktop {
    application {
        mainClass = "io.openftba.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "OpenFTBA"
            packageVersion = "0.1.0"
        }
    }
}
