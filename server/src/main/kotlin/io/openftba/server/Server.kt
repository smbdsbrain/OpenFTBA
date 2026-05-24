package io.openftba.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.http.HttpStatusCode
import io.openftba.analytics.AnalyzerConfig
import io.openftba.analytics.AthleteProfile
import io.openftba.api.AppConfigDto
import io.openftba.dem.SrtmElevationProvider
import io.openftba.model.SensorChannel
import io.openftba.settings.AppSettings
import io.openftba.settings.Sex
import io.openftba.settings.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

private fun env(name: String, default: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

fun main() {
    val port = env("OPENFTBA_PORT", "8080").toInt()
    val watchFolder = env("OPENFTBA_WATCH_FOLDER", "sample-tracks")
    val rescanSeconds = env("OPENFTBA_RESCAN_SECONDS", "60").toLong()
    val ignoreElevation = env("OPENFTBA_IGNORE_ELEVATION", "false").toBoolean()

    val demFolder = System.getenv("OPENFTBA_DEM_FOLDER")?.takeIf { it.isNotBlank() }?.let { File(it) }
    val demAvailable = demFolder?.isDirectory == true
    val useDem = env("OPENFTBA_USE_DEM", "false").toBoolean() && demAvailable
    val demProvider = if (demAvailable) SrtmElevationProvider(demFolder!!) else null
    if (useDem) println("[openftba] DEM elevation enabled from $demFolder")

    val disabled = (System.getenv("OPENFTBA_DISABLED_CHANNELS") ?: "")
        .split(",").mapNotNull { runCatching { SensorChannel.valueOf(it.trim().uppercase()) }.getOrNull() }.toSet()

    // Defaults seeded from env; overridden by the persisted settings file once it exists.
    val envSettings = AppSettings(
        watchFolder = watchFolder,
        demFolder = demFolder?.path,
        ignoreElevation = ignoreElevation,
        useDemElevation = useDem,
        disabledChannels = disabled.map { it.name }.toSet(),
        units = runCatching { UnitSystem.valueOf(env("OPENFTBA_UNITS", "METRIC").uppercase()) }.getOrDefault(UnitSystem.METRIC),
        languageCode = env("OPENFTBA_LANG", "en"),
        weightKg = System.getenv("OPENFTBA_WEIGHT_KG")?.toDoubleOrNull(),
        sex = runCatching { Sex.valueOf(env("OPENFTBA_SEX", "UNSPECIFIED").uppercase()) }.getOrDefault(Sex.UNSPECIFIED),
        maxHr = System.getenv("OPENFTBA_MAX_HR")?.toIntOrNull(),
        restHr = System.getenv("OPENFTBA_REST_HR")?.toIntOrNull(),
        ftpWatts = System.getenv("OPENFTBA_FTP")?.toIntOrNull(),
    )
    // Persisted, cross-device settings. A writable dir (e.g. a Docker volume) makes them
    // survive restarts; without it, settings are in-memory only.
    val configFile = System.getenv("OPENFTBA_CONFIG_DIR")?.takeIf { it.isNotBlank() }
        ?.let { File(it, "settings.json") }

    val store = RideStore(File(watchFolder))
    val settings = SettingsManager(store, configFile, envSettings, demProvider, demAvailable, watchFolder, demFolder?.path)
    store.setConfig(settings.build())
    val count = store.rescan()
    println("[openftba] watching '$watchFolder' — $count rides; serving on :$port" +
        (configFile?.let { "; settings at $it" } ?: "; settings in-memory"))

    val webappDir = System.getenv("OPENFTBA_WEBAPP_DIR")?.takeIf { it.isNotBlank() }
        ?.let { File(it) }?.takeIf { it.isDirectory }

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module(store, settings, rescanSeconds, webappDir)
    }.start(wait = true)
}

/**
 * Owns the persisted [AppSettings] (the single source of truth for every client) and rebuilds
 * the analyzer config when they change. Settings are saved to [configFile] (if provided) so
 * they survive restarts and are shared across devices on the same install. The watch + DEM
 * folders are host-configured, so client changes to them are ignored.
 */
class SettingsManager(
    private val store: RideStore,
    private val configFile: File?,
    envSettings: AppSettings,
    private val demProvider: SrtmElevationProvider?,
    private val demAvailable: Boolean,
    private val watchPath: String?,
    private val demPath: String?,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Volatile
    var settings: AppSettings = load() ?: envSettings
        private set

    private fun load(): AppSettings? = configFile?.takeIf { it.isFile }?.let { f ->
        runCatching { json.decodeFromString(AppSettings.serializer(), f.readText()) }.getOrNull()
    }

    private fun persist() {
        configFile?.let { f ->
            runCatching { f.parentFile?.mkdirs(); f.writeText(json.encodeToString(AppSettings.serializer(), settings)) }
        }
    }

    fun build(): AnalyzerConfig = AnalyzerConfig(
        ignoreElevation = settings.ignoreElevation,
        profile = AthleteProfile(
            weightKg = settings.weightKg, sex = settings.sex,
            maxHr = settings.maxHr, restHr = settings.restHr, ftpWatts = settings.ftpWatts,
        ),
        useDem = settings.useDemElevation && demProvider != null,
        elevationProvider = demProvider,
        disabledChannels = settings.disabledChannels
            .mapNotNull { runCatching { SensorChannel.valueOf(it) }.getOrNull() }.toSet(),
    )

    fun current(): AppConfigDto = AppConfigDto(
        // Reflect the host-configured folders for display.
        settings = settings.copy(watchFolder = watchPath, demFolder = demPath),
        watchFolder = watchPath, demFolder = demPath,
        demAvailable = demAvailable, foldersEditable = false,
    )

    fun apply(incoming: AppSettings): Int {
        // The server owns the folder paths; never let a client overwrite them.
        settings = incoming.copy(watchFolder = watchPath, demFolder = demPath)
        persist()
        store.setConfig(build())
        return store.rescan()
    }
}

@Suppress("OPT_IN_USAGE")
fun Application.module(store: RideStore, settings: SettingsManager, rescanSeconds: Long, webappDir: File? = null) {
    install(ContentNegotiation) {
        json(Json { prettyPrint = false; ignoreUnknownKeys = true })
    }
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText("error: ${cause.message}", status = HttpStatusCode.InternalServerError)
        }
    }

    // Background periodic rescan so newly exported tracks appear automatically.
    if (rescanSeconds > 0) {
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                delay(rescanSeconds * 1000)
                runCatching { store.rescan() }
            }
        }
    }

    routing {
        get("/api/health") { call.respondText("ok") }
        get("/api/settings") { call.respond(settings.current()) }
        post("/api/settings") {
            val dto = call.receive<AppConfigDto>()
            withContext(Dispatchers.IO) { settings.apply(dto.settings) }
            call.respond(settings.current())
        }
        get("/api/overview") { call.respond(store.overview()) }
        get("/api/rides") { call.respond(store.rides) }
        get("/api/rides/{id}") {
            val id = call.parameters["id"]
            val detail = id?.let { store.detail(it) }
            if (detail == null) call.respond(HttpStatusCode.NotFound, "ride not found")
            else call.respond(detail)
        }
        post("/api/rescan") {
            val n = withContext(Dispatchers.IO) { store.rescan() }
            call.respondText("rescanned: $n")
        }
        // The Compose wasmJs web client is the single UI, served at root from a filesystem dir.
        // No-cache: openftba.js / index.html keep stable URLs but change content each build
        // (they reference per-build hashed .wasm), so a stale cached loader 404s the wasm
        // → blank screen. Revalidate to always fetch the current bundle.
        if (webappDir != null) {
            staticFiles("/", webappDir) {
                default("index.html")
                cacheControl { listOf(io.ktor.http.CacheControl.NoCache(null)) }
            }
        }
    }
}
