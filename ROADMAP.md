# OpenFTBA — Roadmap

Privacy-first, local-only cycling analytics, integrating with OpenTracks via the
filesystem. No telemetry, no network at runtime. First iteration: **Biking only**.

The full design lives in the approved plan. This file tracks what is **deferred**
beyond the first vertical slice so the plan file is not the only record.

## ✅ Wave 0 — Vertical slice (in progress)
- Gradle KMP + Compose Multiplatform scaffold (desktop-first).
- KMZ/KML parser for the OpenTracks schema (MultiTrack segments, `lon lat ele`
  coords, `SimpleArrayData` for speed/cadence/heartrate/power/temperature, `trackid`).
- Core analytics: distance, moving/elapsed time, avg/max speed·cadence·HR,
  elevation gain (barometer → smoothed GPS), longest non-stop distance, biggest
  climb, per-km splits, `AvailableChannels` detection.
- Persistence (SQLDelight) + folder import with dedup by `trackid`.
- Screens: Ride list → Ride detail (time/distance-axis charts) → Overview dashboard
  (core metrics + calendar heatmap + a few trend charts).
- Minimal settings: watch folder, DEM folder, elevation source toggle, profile.
- i18n scaffold (EN + RU) from the start.
- Dark design system + custom Canvas charts.
- Runs on desktop against real tracks; unit tests for parser + analytics.

## ✅ Wave 1 — Intensity & gamification (done)
- `effortScore` with graceful degradation (`shared/analytics/Intensity.kt`):
  - Power + FTP → IF = NP/FTP, TSS-like effort. Banister TRIMP available for HR load.
  - HR → IF from %HRR (~85% HRR ≈ threshold).
  - Speed-only → IF vs reference pace, flagged low-confidence (`~`).
- 5 auto intensity tiers per ride (Recovery → Threshold Burn), `IntensityTier.fromIntensityFactor`.
- S–F athlete scale (`shared/analytics/FitnessScale.kt`): Coggan W/kg bands (female shifted
  −0.4); speed proxy when no power meter. Surfaced in desktop UI + web dashboard.
- Refinements still open: power VI, hrTSS as a separate number, calibrate the speed proxy.

## ⚙️ Interim — Self-hosted web server (done; partial Wave 6)
- `server/` Ktor module reuses the shared engine; REST API, deployable as a Docker stack that
  reads the OpenTracks export dir read-only. (An early dependency-free HTML/JS dashboard was the
  interim UI; it was later replaced by the canonical Compose **wasmJs** web client below.)

## ✅ Wave 2 — Elevation accuracy (done)
- DEM correction from local SRTM `.hgt`/`.hgt.gz` tiles with bilinear interpolation
  (`shared/dem/SrtmElevationProvider.kt`); selectable per `AnalyzerConfig` with graceful
  fallback DEM → smoothed GPS → none. Verified: GPS gains 274–970 m → realistic 21–50 m.
- User-initiated tile download (`shared/dem/SrtmDownloader.kt`, public AWS skadi mirror —
  the only networked code path), wired to a desktop Settings button + `useDemElevation` toggle.
- Server reads `OPENFTBA_DEM_FOLDER` / `OPENFTBA_USE_DEM`; the homelab image bakes the
  СПб-region tiles and ships with DEM **on** by default.

## ✅ Wave 3 — Deeper analytics (done)
- CTL / ATL / TSB (fitness / fatigue / form) load curve from daily effortScore
  (`shared/analytics/TrainingLoad.kt`, EWMA τ=42/7); in `/api/overview` + both front ends.
- Distributions: intensity-tier donut, ride-distance histogram, monthly seasonality (overview);
  time-in-HR-zone + cadence↔speed scatter (ride detail, shown only when channels exist).
- Still open (need power data we don't have yet): HR↔power scatter, power curve, HR decoupling.

## ✅ Wave 4 — Sharing (done)
- Share card: dark 1080×1080 image (hero stat, intensity-tier badge, sparkline, stat grid,
  branding) + localized share text.
- Web (`server/.../web/share.js`): per-ride + athlete-tier share via the Web Share API
  (with the PNG file), falling back to download + copy-text; toast feedback.
- Desktop (`composeApp` `ui/share/`, `expect/actual`): renders the card with AWT, saves a
  PNG to `~/OpenFTBA/` and copies the text to the clipboard; button on the ride detail.
- Android `ACTION_SEND` lands with the Android target (Wave 6).

## ✅ Wave 5 — Full settings & sensor trust (done)
- Per-sensor trust: disable HR/cadence/power/speed/elevation even if present — the channel
  is nulled up front (`AnalyzerConfig.disabledChannels`, `RideAnalyzer.stripDisabled`), so
  detection/metrics/intensity/charts all treat it as absent (verified live: HR off →
  intensity falls back HEART_RATE→SPEED).
- Desktop: "Trusted sensors" toggles in Settings (`AppSettings.disabledChannels`).
- Server: runtime `GET/POST /api/settings` (ignoreElevation, useDem, disabledChannels) →
  reconfigures + rescans in place; env `OPENFTBA_DISABLED_CHANNELS` seeds defaults.
- Web: a Settings screen with elevation + per-sensor toggles, applied live.
- Units already metric/imperial (desktop); locale EN/RU everywhere. Theme-accent presets
  deferred (cosmetic).

## ⚙️ Wave 6 — Platform parity (web ✅ · Android deferred)
- ✅ Canonical **Compose wasmJs** web client: same Compose screens as desktop, thin REST
  client (`WasmRideRepository`) to the Ktor server; overview analytics (TrainingLoad/
  FitnessScale) run client-side on mapped rides. Wire contract unified in `shared/api`
  (DTOs + mappers, `buildRideSeries`). Served by Ktor at the root `/` — the single web UI
  (the earlier hand-written HTML dashboard was removed).
- ✅ Share works in the wasm app too: `exportShareCard` serializes the spec to JSON and
  calls bundled `share.js` (Web Share API / download + copy), at parity with desktop.
- ✅ Ktor server (done earlier): watch folder + REST + serves both web UIs.
- ✅ Android app (Compose): same screens; reads the OpenTracks export folder via SAF
  (`ACTION_OPEN_DOCUMENT_TREE` + persistable permission); Android KMZ/KML parser on
  `XmlPullParser` (`AndroidOpenTracksParser`, since Android has no StAX); settings in
  SharedPreferences; share via `ACTION_SEND` (Bitmap card + FileProvider). Manifest ships
  **without `INTERNET`** (verified: only the auto AndroidX internal receiver permission).
  Build: `:composeApp:assembleDebug` → `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.
  Toolchain: AGP 8.7.3, compileSdk 35, `android.useAndroidX=true`, Android SDK via
  `local.properties` `sdk.dir` (command-line SDK tools are enough — no Android Studio required).
- GPX 1.1 (`gpxtpx`) import remains a bonus.

## Open follow-ups
- Calibrate proxy fitness-score thresholds for S–F tiers on real tracks.
- Choose SRTM tile source + storage format for the download button.
- Charting strategy: pure Canvas vs a light Compose charts lib for standard charts.
