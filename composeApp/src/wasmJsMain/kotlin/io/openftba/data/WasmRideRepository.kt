package io.openftba.data

import io.openftba.api.AppConfigDto
import io.openftba.api.RideDetailDto
import io.openftba.api.RideSummaryDto
import io.openftba.api.toRide
import io.openftba.settings.AppSettings
import kotlinx.browser.window
import org.w3c.fetch.RequestInit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Web client repository: a thin REST client to the Ktor server (same origin). Parsing and
 * analytics already ran server-side; here we deserialize the shared DTOs and map them back
 * to domain objects so the exact same Compose screens render. Overview analytics
 * (TrainingLoad / FitnessScale) run locally on the mapped rides, just like desktop.
 */
class WasmRideRepository : RideRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // Snapshot-backed so a screen waiting on a deep-linked detail recomposes when the
    // prefetch lands (a plain HashMap would leave it stuck on the loading state).
    private val details = androidx.compose.runtime.mutableStateMapOf<String, RideDetail>()
    private val _state = MutableStateFlow(RepoState(loading = true))
    override val state: StateFlow<RepoState> = _state.asStateFlow()

    init { loadSettings(); load() }

    override fun updateSettings(settings: AppSettings) {
        // Optimistic local update (snappy locale/units switch), then persist on the server so
        // the change survives refresh and syncs to other devices; reload rides afterwards
        // since profile/elevation/sensor changes re-run server-side analysis.
        _state.update { it.copy(settings = settings) }
        postSettings(settings)
    }

    override suspend fun rescan() = load()

    /** Pull the server's persisted config (settings + read-only folder paths) on startup. */
    private fun loadSettings() {
        fetchText("/api/settings") { body ->
            val cfg = runCatching { json.decodeFromString(AppConfigDto.serializer(), body) }.getOrNull() ?: return@fetchText
            _state.update {
                it.copy(settings = cfg.settings, foldersEditable = cfg.foldersEditable, demAvailable = cfg.demAvailable)
            }
        }
    }

    private fun postSettings(settings: AppSettings) {
        val body = json.encodeToString(AppConfigDto.serializer(), AppConfigDto(settings))
        val init = postInit(body)
        window.fetch("/api/settings", init).then { resp ->
            resp.text().then { text ->
                val cfg = runCatching { json.decodeFromString(AppConfigDto.serializer(), text.toString()) }.getOrNull()
                if (cfg != null) {
                    _state.update {
                        it.copy(settings = cfg.settings, foldersEditable = cfg.foldersEditable, demAvailable = cfg.demAvailable)
                    }
                    load() // server re-analyzed; refresh rides + details
                }
                null
            }
            null
        }.catch { null }
    }

    override fun detail(id: String): RideDetail? = details[id]

    private fun load() {
        fetchText("/api/rides") { body ->
            val summaries = runCatching {
                json.decodeFromString(ListSerializer(RideSummaryDto.serializer()), body)
            }.getOrNull() ?: emptyList()
            _state.update { it.copy(rides = summaries.map { s -> s.toRide() }, loading = false, error = null) }
            // Prefetch detail for each ride so opening one renders immediately.
            summaries.forEach { s -> fetchDetail(s.id) }
        }
    }

    private fun fetchDetail(id: String) {
        fetchText("/api/rides/$id") { body ->
            val dto = runCatching { json.decodeFromString(RideDetailDto.serializer(), body) }.getOrNull() ?: return@fetchText
            details[dto.summary.id] = RideDetail(dto.summary.toRide(), dto.series, dto.splits)
        }
    }

    private fun fetchText(url: String, onText: (String) -> Unit) {
        window.fetch(url).then { resp ->
            resp.text().then { body ->
                onText(body.toString())
                null
            }
            null
        }.catch { err ->
            _state.update { it.copy(loading = false, error = err.toString()) }
            null
        }
    }
}

/** Build a JSON POST RequestInit (must be top-level for Kotlin/Wasm js() interop). */
private fun postInit(body: String): RequestInit =
    js("({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: body })")
