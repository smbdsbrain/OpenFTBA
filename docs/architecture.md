# Architecture

## Stack

Kotlin Multiplatform + Compose Multiplatform. One portable core (`shared`), several front
ends. Versions kept conservative for reliable builds: Kotlin **2.1.20**, Compose MP
**1.8.0**, Gradle **8.13**, Ktor **3.1.1**, JDK **17**.

## Modules

```
shared/                      Kotlin Multiplatform (jvm + wasmJs). The single source of truth.
  commonMain/
    model/        Track.kt, Ride.kt        — domain types (GeoPoint, TrackSegment,
                                              ParsedTrack, Ride, RideMetrics, AvailableChannels)
    analytics/    Geo.kt                    — haversine distance
                  RideAnalyzer.kt           — all per-ride metrics + AnalyzerConfig
                  Intensity.kt              — effortScore, IF, 5 intensity tiers, TRIMP, NP
                  FitnessScale.kt           — S–F athlete tier (Coggan W/kg or speed proxy)
                  TrainingLoad.kt           — CTL/ATL/TSB (fitness/fatigue/form)
                  Elevation.kt              — ElevationProvider interface, ElevationMode
    settings/     AppSettings.kt            — persisted settings (serializable)
    api/          Dto.kt, Mappers.kt        — the wire contract (DTOs) + domain<->DTO mappers
                                              + buildRideSeries (chart series from a track)
    cache/        RideCache.kt              — portable per-ride analysis cache: format,
                                              config fingerprint, invalidation rules
  jvmMain/
    parse/        OpenTracksKmlParser.kt    — KMZ/KML (OpenTracks schema) reader (StAX)
    cache/        JvmRideCache.kt           — desktop+server disk cache (java.io)
    dem/          SrtmElevationProvider.kt  — local SRTM .hgt/.hgt.gz reader (bilinear)
                  SrtmDownloader.kt         — user-initiated tile download (only network path)

composeApp/                  Compose UI, targets: desktop (jvm) + wasmJs.
  commonMain/ui/
    App.kt                    — nav rail + screen switching
    theme/Theme.kt            — dark palette + Material3 scheme
    i18n/Strings.kt           — EN + RU type-safe string catalog
    format/Format.kt          — SI -> display formatting (metric/imperial)
    components/, charts/       — StatTile/Pill, LineChart/BarChart/DonutChart/CalendarHeatmap
    screens/                   — OverviewScreen, RideListScreen, RideDetailScreen, SettingsScreen
    overview/OverviewStats.kt  — overview aggregation (totals, records, heatmap)
    share/ShareCard.kt         — ShareSpec + `expect fun exportShareCard` (per-platform)
    data/RideRepository.kt     — repository interface + RepoState + RideDetail
  desktopMain/
    Main.kt                          — desktop entry
    data/DesktopRideRepository.kt    — parses the folder in-process (java.io)
    ui/share/ShareCard.desktop.kt    — AWT renderer -> PNG + clipboard
  wasmJsMain/
    main.kt                          — CanvasBasedWindow entry
    data/WasmRideRepository.kt       — thin REST client to the Ktor server
    ui/share/ShareCard.wasm.kt       — calls bundled share.js (Web Share / download)
    resources/index.html, share.js   — web bootstrap + share-card drawing

server/                      Ktor (JVM). Reuses `shared`.
  Server.kt                  — main, REST routes, static serving, SettingsManager
  RideStore.kt               — scans folder, parses+analyzes, caches DTOs, runs overview
```

## Data flow

```
OpenTracks app  ──exports .kmz──►  watch folder  ──read-only──►  OpenFTBA
                                                                    │
        OpenTracksKmlParser ─► ParsedTrack ─► RideAnalyzer ─► Ride + RideMetrics
                                                                    │
                         api.Mappers: toSummaryDto / buildRideSeries / toRide
                                                                    │
   desktop: in-process ──────────────┐         server: REST (JSON DTOs)
                                      ▼                     ▼
                          Compose screens (commonMain) — desktop · Android · wasm web
```

- **Desktop**: `DesktopRideRepository` parses the folder and holds domain `Ride` +
  `RideDetail` (with `RideSeriesDto` + the raw `ParsedTrack`, the latter only used to derive
  which DEM tiles to download).
- **Server**: `RideStore` parses the folder, caches `RideSummaryDto` / `RideDetailDto` /
  `OverviewDto`, and exposes them over REST.
- **Web (wasm)**: `WasmRideRepository` fetches the DTOs and maps them back to domain `Ride`
  so the exact same Compose screens render; overview analytics (TrainingLoad/FitnessScale)
  run client-side on the mapped rides.

## Wire contract (`shared/api`)

The DTOs in `shared/src/commonMain/.../api/Dto.kt` are the single contract used by the
server (maps domain → DTO) and the web client (parses DTO → domain). Times are epoch millis;
all other units are SI. Mappers: `Ride.toSummaryDto()`, `RideSummaryDto.toRide()`,
`buildRideSeries(track): RideSeriesDto`. Add a field once here and both ends share it.

REST endpoints (server):
- `GET /api/rides` → `List<RideSummaryDto>`
- `GET /api/rides/{id}` → `RideDetailDto` (summary + downsampled series + splits)
- `GET /api/overview` → `OverviewDto` (athlete tier + CTL/ATL/TSB load curve)
- `GET /api/status` → `ScanStatusDto` (scan progress for the "computing N new rides" banner)
- `GET/POST /api/settings` → `AppConfigDto` (the full `AppSettings` + read-only watch/DEM folder
  paths + `demAvailable`). POST persists the settings, reconfigures the analyzer and rescans in
  place. Folder paths are host-configured, so client changes to them are ignored.
- `POST /api/rescan`, `GET /api/health`
- static: the Compose wasm bundle is served at the root `/` (the single web UI)

## Storage / state (important)

- **No database.** Rides are parsed from the watch folder on each scan and held **in
  memory** (desktop: a map in `DesktopRideRepository`; server: an `AtomicReference<Snapshot>`
  in `RideStore`). The server periodically rescans (`OPENFTBA_RESCAN_SECONDS`, default 60).
- **On-disk analysis cache.** To avoid re-parsing and re-analyzing every track on each launch,
  the analyzed result of each ride (a `RideDetailDto` — metrics + downsampled chart/3D series +
  splits) is cached as one JSON blob per source file, keyed by file identity (name + size +
  mtime) + a fingerprint of the `AnalyzerConfig` + a cache version. On scan, unchanged files are
  restored from the cache and only new/changed files are parsed and analyzed (incrementally,
  with a UI "computing N new rides" banner driven by `RepoState.analyzing*` / the server's
  `GET /api/status`). The portable format + invalidation rules live in `shared/.../cache/`
  (`RideCache.kt`); IO is platform-specific (`JvmRideCache` for desktop+server, `AndroidRideCache`
  for Android). Cache location: `~/.openftba/cache/` (desktop), `OPENFTBA_CONFIG_DIR/cache/`
  (server, skipped if unset), app `filesDir/ride-cache/` (Android). Changing the analyzer config
  invalidates all entries (one full re-analyze); a DEM download clears the cache. **Bump
  `RIDE_CACHE_VERSION` whenever `RideAnalyzer`/`buildRideSeries` change computed numbers.**
- **Settings**: desktop persists `AppSettings` as JSON at `~/.openftba/settings.json`; Android
  uses SharedPreferences. The server persists the full `AppSettings` to JSON at
  `OPENFTBA_CONFIG_DIR` (seeded from env on first run), so settings survive restarts and sync
  across every client of that install. The web client loads/saves through the server, so a page
  refresh no longer loses settings.
- **DEM tiles**: SRTM `.hgt`/`.hgt.gz`, 1° per tile, read from a folder. In the container build
  the region tiles are baked into the image and `OPENFTBA_DEM_FOLDER` points at them.
- **Source tracks are never modified** (read-only) and never committed.

## Deployment

The server is a plain JVM app, so any host or container with a JRE works. The reference setup is
a small Docker image that **bakes the locally-built artifacts** — Kotlin is never built inside
the container. It mounts the OpenTracks export folder **read-only**, mounts a writable volume for
persisted settings, and serves the single Compose wasm UI at root `/` with `Cache-Control:
no-cache` and `<base href="/">` (each build references a per-build hashed `.wasm`, so a stale
cached loader would 404 → blank screen).

**Build the artifacts** (then copy them into your image build context):
```bash
./gradlew :server:installDist                     # → server/build/install/server/
./gradlew :composeApp:wasmJsBrowserDistribution   # → composeApp/build/dist/wasmJs/productionExecutable/  (if web/UI changed)
```
Point the container at the folders with env vars (`OPENFTBA_WATCH_FOLDER`, `OPENFTBA_WEBAPP_DIR`,
`OPENFTBA_DEM_FOLDER`, `OPENFTBA_CONFIG_DIR`, …) — see [`AGENTS.md`](../AGENTS.md) for the full
list. Operator-specific values (host, paths, secrets) stay in a local, untracked notes file.

## How to extend

- **Add a per-ride metric**: compute it in `RideAnalyzer`, add a field to `RideMetrics`
  (`shared/model/Ride.kt`), expose it in `RideSummaryDto` + `toSummaryDto`/`toRide`
  (`shared/api`), then show it in the screens (`composeApp/.../screens`). Charts are in
  `composeApp/.../charts` (the single Compose chart renderer: `ChartScaffold`,
  `InteractiveLineChart`, `InteractiveBarChart`, `DonutChart`, `CalendarHeatmap`).
- **Add an overview stat / chart**: extend `OverviewStats` (client-side bits) or
  `TrainingLoad`/`FitnessScale` (shared) + `OverviewDto`.
- **Add a screen**: a composable in `screens/` + wire it into `App.kt`'s nav.
- **Add a wire field**: edit `shared/api/Dto.kt` once; update mappers; rebuild.
- **Strings**: add to the `Strings` interface and both `EnStrings`/`RuStrings`.
- **Share card**: layout lives in three renderers — `ShareCard.desktop.kt` (AWT, desktop),
  `wasmJsMain/resources/share.js` (wasm web), `ShareCard.android.kt` (Android Bitmap). Keep
  them visually in sync.
