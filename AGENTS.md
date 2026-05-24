# AGENTS.md — OpenFTBA working guide

Context and conventions for working on OpenFTBA (human or AI contributors). Read this first,
then [docs/architecture.md](docs/architecture.md) for the module map and [ROADMAP.md](ROADMAP.md)
for what's done and what's next.

## What this is

Privacy-first, **local-only** cycling analytics inspired by FitTrackee, but
analytics-over-maps (there is **no map**). Integrates with OpenTracks purely through the
filesystem: it reads the folder OpenTracks auto-exports to. **No telemetry, no accounts,
no outbound network at runtime.** First iteration: **Biking only**.

## Tech stack & layout

Kotlin Multiplatform + Compose Multiplatform. Modules:

- `shared/` — portable core (KMP). `commonMain`: domain models, analytics, settings, the wire
  contract (`api/`). `jvmMain`: the OpenTracks KMZ/KML parser (StAX) + SRTM DEM reader. This is
  the single source of truth for parsing + metrics, reused by every front end.
  - `model/Track.kt`, `model/Ride.kt` — domain.
  - `analytics/` — `Geo` (haversine), `RideAnalyzer` (metrics), `Intensity`, `FitnessScale`,
    `TrainingLoad`, `Elevation`.
  - `api/Dto.kt` + `Mappers.kt` — DTOs + `toSummaryDto`/`toRide`/`buildRideSeries`.
  - `parse/OpenTracksKmlParser.kt`, `dem/` (jvmMain).
- `composeApp/` — Compose UI. `commonMain`: screens, theme, charts, i18n, format, the repo
  interface. `desktopMain`: `Main.kt` + `DesktopRideRepository` (reads the folder via java.io).
  `androidMain`: Android app + `AndroidOpenTracksParser` (XmlPullParser — Android has no StAX).
  `wasmJsMain`: `CanvasBasedWindow` entry + `WasmRideRepository` (thin REST client).
- `server/` — Ktor (JVM) web server. Reuses `shared`. Serves the REST API + the Compose
  **wasmJs** bundle at root `/` (the single web UI). Entry: `io.openftba.server.ServerKt`.

Versions (kept conservative for reliable builds): Kotlin **2.1.20**, Compose MP **1.8.0**,
Gradle **8.13**, Ktor **3.1.1**, AGP **8.7.3**, compileSdk **35** (min 26). JDK **17**.

## Build & run

Use the Gradle wrapper (`./gradlew`, or `gradlew.bat` on Windows):

```bash
./gradlew :composeApp:run                      # desktop app
./gradlew :composeApp:assembleDebug            # Android APK (needs local.properties sdk.dir)
./gradlew :composeApp:wasmJsBrowserDistribution# web bundle → composeApp/build/dist/wasmJs/productionExecutable
./gradlew :server:installDist                  # web server dist → server/build/install/server
./gradlew :shared:jvmTest                      # unit tests (parser + analytics)
# opt-in tests against a local folder of real tracks (tracks are never committed):
./gradlew :shared:jvmTest -Ptracks="<abs>/sample-tracks"
./gradlew :composeApp:desktopTest -Ptracks="<abs>/sample-tracks"
```

Run the server locally:
```bash
OPENFTBA_WATCH_FOLDER="$(pwd)/sample-tracks" OPENFTBA_PORT=8099 \
  ./server/build/install/server/bin/server
# REST: /api/health /api/rides /api/rides/{id} /api/overview /api/settings (GET/POST)
#       POST /api/rescan ; UI at /
```

Android SDK: set `sdk.dir` in `local.properties` (gitignored). A full Android Studio install
is not required — the command-line SDK tools are enough.

## OpenTracks data format (verified on real tracks)

Export is **`.kmz`** = ZIP with one `doc.kml` (KML 2.3, `opentracksapp.com/xmlschemas/v1`):
- `<opentracks:trackid>` (UUID) for dedup; `activityType` = `biking`.
- `<MultiTrack>` → `<Track>` segments; **segment boundary = pause**.
- Per segment: parallel `<when>` + `<coord>` (**order `lon lat ele`**, meters; empty
  `<coord/>` = no GPS fix → skipped), then `SimpleArrayData` arrays index-aligned with
  `<when>`: `trackpoint_type`, `speed`, `cadence`, `heartrate`, `power`, `temperature`,
  `accuracy_horizontal`. Empty `<value/>` = no reading.
- Channel detection drives graceful degradation: charts/cards hide when a sensor is absent.

## Privacy (tracks)

Real OpenTracks exports contain personal GPS data and **must never be committed**. `.gitignore`
excludes `*.kmz`/`*.kml`/`*.gpx`, `/sample-tracks/`, `/test-tracks/`, `/dem/`. Keep a local debug
copy in `sample-tracks/` if you like — it stays out of git. Source-tracks are read **read-only**
and never modified.

## Self-hosting / deployment (pattern)

The server is a plain JVM app, so any container or host that can run a JRE works. The reference
setup is a small Docker image that **bakes locally-built artifacts** (Kotlin is never built
inside Docker):

1. Build locally: `./gradlew :server:installDist` and (if the web UI changed)
   `:composeApp:wasmJsBrowserDistribution`.
2. Copy `server/build/install/server/` (and the wasm `productionExecutable/`, and any SRTM DEM
   tiles) into the image build context, then build + run the image.
3. Mount your OpenTracks export folder **read-only** and point `OPENFTBA_WATCH_FOLDER` at it.
   Mount a writable volume and set `OPENFTBA_CONFIG_DIR` so settings persist across restarts.

Server env vars: `OPENFTBA_PORT`, `OPENFTBA_WATCH_FOLDER`, `OPENFTBA_RESCAN_SECONDS`,
`OPENFTBA_DEM_FOLDER`, `OPENFTBA_USE_DEM`, `OPENFTBA_WEBAPP_DIR`, `OPENFTBA_CONFIG_DIR`,
`OPENFTBA_IGNORE_ELEVATION`, `OPENFTBA_DISABLED_CHANNELS`,
`OPENFTBA_WEIGHT_KG/SEX/MAX_HR/REST_HR/FTP`, `OPENFTBA_UNITS`, `OPENFTBA_LANG`.

The wasm UI is served at root `/` with `<base href="/">` and `Cache-Control: no-cache` (each
build references a per-build hashed `.wasm`, so a stale cached loader would 404 → blank screen).

> Operator-specific values (your own host, paths, secrets) belong in a local, untracked notes
> file — not in this repo.

## Design system

Dark "instrument cluster", not default Material. Palette in `composeApp/.../ui/theme/Theme.kt`
(base `#0C0E12`, surface `#14171D`, accent `#5BE3C8`, record/power `#FFB347`, HR `#FF6B8A`,
elevation `#9B8CFF`, cadence `#B5F23D`). Charts are dependency-free Compose Canvas in
`ui/charts/`: `ChartScaffold` (axes/ticks/cursor/tooltip via `AxisSpec`), `InteractiveLineChart`
(labelled axes, hover crosshair+tooltip, synced cursor, min/avg/max markers, pause/segment
spans), `InteractiveBarChart`, `DonutChart`, `CalendarHeatmap`. Metric info popups live in
`ui/info/MetricInfo.kt`. i18n EN + RU everywhere (`ui/i18n/Strings.kt`).

## Conventions

- All internal units are SI (meters, m/s, seconds, bpm, rpm, W, °C); convert only at display.
- A nullable metric means the sensor was absent; never fabricate values.
- Keep the parser/analytics in `shared` so desktop, server, web and Android agree on the numbers.
- One UI codebase: the Compose screens run on desktop, Android, and the wasmJs web client. The
  web client is a thin REST client to the Ktor server; desktop/Android parse + analyze in-process.
- When changing analytics, change the engine in `shared`; the single Compose UI then surfaces it
  everywhere. Add a wire field once in `shared/api/Dto.kt` and update the mappers.
