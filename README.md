# OpenFTBA

**Privacy-first, local-only cycling analytics.** Inspired by FitTrackee, but focused on
**analytics and progress, not maps** — there is deliberately no map. OpenFTBA reads the tracks
[OpenTracks](https://codeberg.org/OpenTracksApp/OpenTracks) already exports to your filesystem,
computes a rich set of cycling metrics, and shows them in a dark "instrument cluster" UI.

**No telemetry. No accounts. No cloud. No outbound network at runtime.** Your training data
stays on your machine (or your own server).

> First iteration: **cycling only**.

## Philosophy

- **Your data is yours.** Nothing is uploaded, no account is required, there is no analytics or
  phone-home. The Android app ships **without the `INTERNET` permission**.
- **Local-first.** OpenFTBA reads an OpenTracks export folder **read-only** and never modifies
  or transmits your tracks. Real activity files are never committed to this repo (the
  `.gitignore` enforces it).
- **One honest number.** Parsing and analytics live in a single shared engine, so desktop,
  Android and web always agree.
- **No lock-in, no monetization.** Free and open source under [WTFPL](LICENSE).
- The only opt-in network path is a **user-initiated** SRTM elevation-tile download, off by
  default.

## How it works

```
OpenTracks app  ──auto-exports .kmz──►  a watch folder  ──read-only──►  OpenFTBA
                                                                           │
            shared parser ─► analytics engine ─► metrics, intensity, load curve …
                                                                           │
                         the same Compose UI on  desktop · Android · web
```

Point OpenFTBA at the folder OpenTracks auto-exports to. It parses each `.kmz`/`.kml`, computes
metrics, classifies ride intensity, tracks your fitness over time, and draws per-ride and
overview dashboards. On desktop and Android it parses in-process; on the web it's a thin client
to a small [Ktor](https://ktor.io) server you can self-host (e.g. on a NAS or homelab) so you can
open the same data from any device on your network.

## Features

- **Core metrics** — distance, moving/elapsed time, avg·max speed/cadence/HR/power, elevation
  gain/loss, longest non-stop distance, biggest climb, per-km splits, automatic per-sensor
  channel detection (charts hide when a sensor is absent).
- **Auto intensity** — every ride classified into 5 tiers (Recovery → Threshold Burn) from an
  Intensity Factor, using power if available, else heart rate, else speed.
- **S–F athlete scale** — gamified level anchored to Coggan FTP W/kg benchmarks (or a speed
  proxy without a power meter).
- **Training load** — CTL/ATL/TSB (fitness / fatigue / form) curve with practical guidance.
- **DEM elevation correction** — replaces noisy GPS altitude with local SRTM tiles (bilinear
  interpolation), with graceful fallback.
- **Per-sensor trust** — distrust a flaky sensor and it's treated as absent everywhere.
- **Interactive charts** — labelled axes, hover crosshair + tooltips, cursors synced across a
  ride's charts, min/avg/max markers, and pause/segment overlays. Dependency-free Compose Canvas.
- **Metric info popups** — every metric explains what it is, its source (GPS / sensor / DEM /
  calculated) and formula; categorical metrics list their full scale, with links to references.
- **Share cards** — a generated dark achievement image + text.
- **Bilingual** — full English + Russian UI.

## Where it runs

One engine, several front ends — the parser and analytics live once in `shared`.

| Target | What | Status |
|---|---|---|
| **Desktop** (Windows/Linux) | Compose Multiplatform; reads the folder directly | ✅ |
| **Android** | Compose app; reads the export folder via SAF; no `INTERNET` permission | ✅ |
| **Web + server** | Ktor server reads the folder + REST API; serves the Compose wasm UI at `/` | ✅ |

## Build & run

Requires **JDK 17+**. Use the Gradle wrapper (`./gradlew`, or `gradlew.bat` on Windows).

```bash
# Desktop app
./gradlew :composeApp:run

# Android APK (needs local.properties with sdk.dir=/path/to/Android/sdk)
./gradlew :composeApp:assembleDebug
#   → composeApp/build/outputs/apk/debug/composeApp-debug.apk

# Web bundle + self-hosted server
./gradlew :composeApp:wasmJsBrowserDistribution   # → composeApp/build/dist/wasmJs/productionExecutable
./gradlew :server:installDist                     # → server/build/install/server
OPENFTBA_WATCH_FOLDER=/path/to/tracks ./server/build/install/server/bin/server   # UI at http://localhost:8080

# Unit tests (parser + analytics)
./gradlew :shared:jvmTest
```

On desktop, open **Settings** and set the OpenTracks export folder. For the server, set
`OPENFTBA_WATCH_FOLDER` (see [`AGENTS.md`](AGENTS.md) for all env vars and the self-hosting
pattern).

### Self-host with Docker

A multi-stage [`Dockerfile`](Dockerfile) builds everything from source, so a clone + one command
is enough:

```bash
cp example.docker-compose.yaml docker-compose.yaml
cp example.env .env
# edit .env — set OPENFTBA_TRACKS to your OpenTracks export folder
docker compose up --build -d        # web UI at http://localhost:5412
```

Your tracks are mounted **read-only**; settings persist in a named volume. See
[`example.env`](example.env) for all options.

### Testing against real tracks (optional)

Your activity tracks are never committed (`.gitignore` excludes `*.kmz`/`*.kml`/`*.gpx`). To run
the opt-in tests against a local folder:

```bash
./gradlew :shared:jvmTest         -Ptracks="/abs/path/to/tracks"
./gradlew :composeApp:desktopTest -Ptracks="/abs/path/to/tracks"
```

## Tech stack

Kotlin Multiplatform + Compose Multiplatform. Kotlin 2.1.20 · Compose MP 1.8.0 · Gradle 8.13 ·
Ktor 3.1.1 · AGP 8.7.3 · compileSdk 35 · JDK 17. No database — rides are parsed from the watch
folder and held in memory. Charts are hand-rolled, dependency-free Compose Canvas.

## Documentation

- [docs/architecture.md](docs/architecture.md) — modules, data flow, wire contract, storage, deployment.
- [docs/metrics-and-formulas.md](docs/metrics-and-formulas.md) — every metric and its exact formula.
- [AGENTS.md](AGENTS.md) — working guide for contributors, incl. the self-hosting pattern.
- [CONTRIBUTING.md](CONTRIBUTING.md) — how to build, test and contribute.

## License

[WTFPL](LICENSE) — do whatever you want. Privacy is the point.

```bash
git clone https://github.com/smbdsbrain/OpenFTBA
```
