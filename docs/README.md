# OpenFTBA — Documentation

Privacy-first, **local-only** cycling analytics. Inspired by FitTrackee, but focused on
**analytics & progress, not maps** (there is no map). Integrates with
[OpenTracks](https://codeberg.org/OpenTracksApp/OpenTracks) purely through the filesystem:
it reads the folder OpenTracks auto-exports to. No telemetry, no accounts, no outbound
network at runtime. First iteration: **cycling only**.

## Docs index

- [architecture.md](architecture.md) — modules, data flow, wire contract, storage, deploy.
- [metrics-and-formulas.md](metrics-and-formulas.md) — every metric and its exact formula.
- Repo root also has: `ROADMAP.md` (what's done / next, by wave) and `AGENTS.md`
  (working guide for contributors/agents, incl. the self-hosting pattern).

## What it does

Point it at the OpenTracks export folder; it parses each track (KMZ/KML), computes a rich
set of cycling metrics, classifies ride intensity, gamifies your level on an S–F scale,
draws dashboards (overview + per-ride), and lets you share a generated achievement card.

## Where it runs (one engine, several front ends)

| Target | What | Status |
|---|---|---|
| **Desktop** | Compose Multiplatform (Win/Linux), reads the folder directly | ✅ |
| **Web (homelab)** | Ktor server reads the folder + REST API; serves the Compose wasmJs UI at `/` | ✅ deployed |
| **Android** | Compose app reading the export folder via SAF | ✅ |

(The Compose web client is the single web UI — same screens as desktop, served at root. An
earlier hand-written HTML dashboard was removed in favour of it.)

The parsing + analytics live once in the `shared` module and are reused everywhere, so all
front ends agree on the numbers.

## Quick start

Requires JDK 17+. Use the Gradle wrapper:

```bash
# Desktop app
./gradlew :composeApp:run

# Web server (serves the Compose wasm UI + REST API at /)
./gradlew :server:installDist
OPENFTBA_WATCH_FOLDER=/path/to/tracks ./server/build/install/server/bin/server

# Tests (parser + analytics)
./gradlew :shared:jvmTest
```

Then set the OpenTracks export folder (desktop: Settings; server: `OPENFTBA_WATCH_FOLDER`).

## Self-hosting

The server is a plain JVM app and runs anywhere a JRE does. The reference setup is a small
Docker image that bakes the locally-built artifacts (Kotlin is never built in the container),
mounts your OpenTracks export folder **read-only**, and serves the single Compose web UI at the
root `/`. Mechanics + env vars are in [architecture.md](architecture.md#deployment) and
[`AGENTS.md`](../AGENTS.md).

## Privacy stance

- No analytics, no accounts, no outbound calls at runtime.
- The Android app will ship **without the `INTERNET` permission**.
- The only networked code path is the **user-initiated** SRTM tile download
  (`shared/dem/SrtmDownloader.kt`), off by default.
- Real activity tracks are never committed (`.gitignore` excludes `*.kmz/*.kml/*.gpx`).
