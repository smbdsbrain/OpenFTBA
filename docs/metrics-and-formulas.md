# Metrics & formulas

All internal units are SI (meters, m/s, seconds, bpm, rpm, watts, °C); conversion happens
only at display. Defaults below are `AnalyzerConfig` values
(`shared/.../analytics/RideAnalyzer.kt`). A nullable metric means the sensor is absent (or
the user disabled it) — the UI hides it rather than showing a fake value.

## Track parsing (OpenTracks KMZ/KML)

`.kmz` = ZIP with one `doc.kml` (KML 2.3, `opentracksapp.com/xmlschemas/v1`):
- `<MultiTrack>` → `<Track>` segments; **a segment boundary is a pause**.
- Per segment: `<when>` (ISO time) + `<coord>` (**order: `lon lat ele`**, meters; empty
  `<coord/>` = no GPS fix → skipped), then index-parallel `<SimpleArrayData>` arrays:
  `trackpoint_type`, `speed`, `cadence`, `heartrate`, `power`, `temperature`,
  `accuracy_horizontal`. Empty `<value/>` = no reading.
- `<opentracks:trackid>` is the dedup key.

## Distance

Haversine between consecutive points (`Geo.haversine`), `R = 6_371_000 m`:

```
a = sin²(Δlat/2) + cos(lat₁)·cos(lat₂)·sin²(Δlon/2)
d = 2R·atan2(√a, √(1−a))
```
Total distance = Σ over all points. **Longest non-stop** = the largest single-segment
distance (segments are pause-separated).

## Time & speed

- **Moving time** = Σ Δt over point pairs whose speed ≥ `movingSpeedThreshold` (1.0 m/s ≈ 3.6 km/h).
- **Elapsed time** = last timestamp − first timestamp.
- **Speed** per point = recorded `speed` if present, else `distance/Δt`; clamped to
  `maxPlausibleSpeed` (30 m/s ≈ 108 km/h) to reject GPS spikes.
- **Avg speed** = total distance / moving time. **Max speed** = max per-point speed.
- Avg/max **cadence**, **heart rate**, **power** = mean / max of present readings.

## Elevation

Source is chosen with graceful fallback (`RideAnalyzer.buildElevationSeries`):
**DEM** (if enabled and it covers ≥50% of the route) → **smoothed GPS** → **none**.
DEM is tried first and is independent of the track's altitude, so **"Ignore GPS elevation"**
(`ignoreElevation`) only drops the smoothed-GPS fallback — it does **not** suppress DEM. The
result is **none** when the track elevation is absent, distrusted, or `ignoreElevation` is set and
no usable DEM is available. Distrusting the ELEVATION sensor channel is the stronger switch: it
strips the channel entirely and also turns DEM off.

- **Smoothing**: moving average over `elevationSmoothingWindow` (7 points) of the elevation
  series; nulls are forward-filled. DEM series uses a light window of 3.
- **Gain / loss** (`elevationGainLoss`) — hysteresis to reject noise: accumulate only when
  the change from the current anchor crosses `elevationThreshold` (2 m), then re-anchor:
  ```
  Δ = ele[i] − anchor
  if Δ ≥ +2m:  gain += Δ; anchor = ele[i]
  if Δ ≤ −2m:  loss += −Δ; anchor = ele[i]
  ```
- **Biggest climb** = largest net rise within one segment, reset when a sustained descent
  exceeds the threshold.

### DEM correction (SRTM)

`SrtmElevationProvider` reads SRTM `.hgt`/`.hgt.gz` tiles (1° squares named after the SW
corner, e.g. `N46E007`; big-endian int16 meters, 1201² = 3″ or 3601² = 1″; voids = −32768)
and **bilinearly interpolates** the four surrounding samples. This replaces noisy GPS
elevation (observed: GPS 274–970 m of "gain" on essentially flat urban rides → realistic 21–50 m).
Tiles are user-supplied or fetched on demand by `SrtmDownloader` (AWS "skadi" mirror — the
only network call, user-initiated).

## Splits

Per `splitMeters` (1000 m). Each split records distance, duration, avg speed, elevation
gain over the split, and avg HR (if present).

## Intensity — `effortScore`, IF, and the 5 tiers

`IntensityCalculator.evaluate` picks the best available signal (graceful degradation):

1. **Power + FTP** → Intensity Factor `IF = NP / FTP` (clamped 0–1.5), source `POWER`.
2. **Heart rate** → `HRr = (avgHR − restHR) / (maxHR − restHR)` (0–1); `IF = HRr / 0.85`
   (≈85% HRR treated as threshold; clamped ≤1.3), source `HEART_RATE`.
3. **Speed only** → `IF = avgSpeed / 7.0 m/s` (≈25 km/h reference; clamped ≤1.3), source
   `SPEED` (flagged low-confidence, shown with a `~`).

```
effortScore = movingHours · IF² · 100      (TSS-like: 1 h all-out ≈ 100)
```

**Normalized Power (NP)**: 30-sample rolling average of power, raised to the 4th power,
meaned, then 4th-rooted.

**Banister TRIMP** (training load from HR), available for HR rides:
```
TRIMP = t_min · HRr · k · e^(b·HRr)     male: k=0.64, b=1.92 ; female: k=0.86, b=1.67
```

**5 intensity tiers** (`IntensityTier.fromIntensityFactor`):

| IF | Tier (key) | RU |
|---|---|---|
| ≥ 0.95 | Threshold Burn (`threshold_burn`) | Взрыв |
| 0.85–0.95 | Race (`race`) | Гонка |
| 0.75–0.85 | Tempo (`tempo`) | Темп |
| 0.65–0.75 | Endurance (`endurance`) | Выносливость |
| < 0.65 | Recovery (`recovery`) | Прогулка |

Profile inputs (`AthleteProfile`): `weightKg`, `sex`, `maxHr` (default 190), `restHr`
(default 60), `ftpWatts`. Desktop reads them from Settings; server from
`OPENFTBA_MAX_HR/REST_HR/WEIGHT_KG/FTP/SEX`.

## S–F athlete scale (`FitnessScale`)

Gamified overall level, anchored to real benchmarks (Coggan FTP W/kg).

**Preferred — power**: `W/kg = FTP / weight` (female compared against male bands via
`+0.4` shift), mapped to lower bounds:

| Tier | W/kg | Reference |
|---|---|---|
| S | ≥ 5.15 | pro / world class |
| A | 4.3–5.15 | Cat 1–2, national amateur |
| B | 3.7–4.3 | strong amateur, Cat 3 |
| C | 2.9–3.7 | good recreational, Cat 4–5 |
| D | 2.2–2.9 | recreational |
| E | 1.5–2.2 | beginner |
| F | < 1.5 | untrained |

**Fallback — speed proxy** (no power meter): best avg ride speed (km/h), bounds
S≥32, A≥28, B≥25, C≥22, D≥19, E≥15, else F (female `+2` km/h shift). Flagged
`SPEED_PROXY` (lower confidence). `progressToNext` (0–1) drives the progress bar.

## Training load — CTL / ATL / TSB (`TrainingLoad`)

Fitness / Fatigue / Form from daily `effortScore`, exponentially-weighted moving averages
(TrainingPeaks-style). Daily load = Σ `effortScore` of that day's rides; rest days = 0.

```
CTL_t = CTL_{t-1} + (load_t − CTL_{t-1}) / 42     (Fitness, 42-day)
ATL_t = ATL_{t-1} + (load_t − ATL_{t-1}) / 7      (Fatigue, 7-day)
TSB_t = CTL_t − ATL_t                              (Form)
```
Computed from the first ride day to today, one point per day. Negative TSB = fatigued;
positive = fresh.

## Overview aggregates (`OverviewStats`)

Totals (rides, distance, moving time, ascent, avg ride distance); records (max speed,
longest non-stop, longest ride, biggest climb, max ascent/ride, best avg speed); a 26-week
GitHub-style calendar heatmap of per-day distance; intensity-tier donut; distance histogram
(5 km / 5 mi buckets); monthly seasonality. Per-ride detail adds time/distance-axis charts,
HR-zone distribution, and a cadence↔speed scatter (shown only when the channel exists).

## Sensor trust

`AnalyzerConfig.disabledChannels` nulls a distrusted channel **before** analysis
(`RideAnalyzer.stripDisabled`), so detection, metrics, intensity and charts all treat it as
absent — e.g. disabling HR makes intensity fall back from `HEART_RATE` to `SPEED`. Disabling
elevation also disables DEM.
