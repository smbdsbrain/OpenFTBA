# Contributing to OpenFTBA

Thanks for your interest! OpenFTBA is a privacy-first, local-only cycling analytics app. The
guiding rule is simple: **it never phones home, and your training data stays yours.** Keep that
true in every change.

## Getting started

Requires **JDK 17+**. Use the Gradle wrapper (`./gradlew`, or `gradlew.bat` on Windows).

```bash
./gradlew :shared:jvmTest          # unit tests (parser + analytics) — start here
./gradlew :composeApp:run          # desktop app
./gradlew :composeApp:assembleDebug# Android APK (needs local.properties with sdk.dir=...)
./gradlew :server:installDist      # web server dist
```

For the architecture, module map and self-hosting pattern, read [AGENTS.md](AGENTS.md) and
[docs/architecture.md](docs/architecture.md).

## Project layout

- `shared/` — the portable core: domain model, the OpenTracks parser, and **all** analytics.
  This is the single source of truth reused by every front end.
- `composeApp/` — the Compose UI (desktop + Android + wasm web), one codebase.
- `server/` — a small Ktor server (REST API + serves the wasm UI), for self-hosting.

## Conventions

- **Keep parsing + analytics in `shared`.** Desktop, Android, web and the server must all agree
  on the numbers, so the engine lives in one place.
- **Internal units are SI** (meters, m/s, seconds, bpm, rpm, W, °C). Convert only at display.
- **A nullable metric means the sensor was absent** — never fabricate values; let the UI
  gracefully hide what isn't there.
- **One wire contract:** add a field once in `shared/api/Dto.kt`, update the mappers, and both
  the server and the web client share it.
- **Strings are bilingual:** add to the `Strings` interface and both `EnStrings` and `RuStrings`
  (`composeApp/.../ui/i18n/Strings.kt`).
- **Charts are dependency-free** Compose Canvas (`composeApp/.../ui/charts/`). Please don't pull
  in a charting library.
- **Run `./gradlew :shared:jvmTest`** before sending a change; add tests for new analytics.

## Privacy rule (important)

**Never commit real activity tracks or GPS data.** The `.gitignore` excludes `*.kmz`/`*.kml`/
`*.gpx`, `/sample-tracks/`, `/dem/`, and machine-specific files. Test fixtures must use clearly
**synthetic** coordinates and timestamps — never a real exported point. Don't add telemetry,
analytics, crash reporting, or any runtime network call (the one exception is the existing,
user-initiated SRTM tile download).

## Submitting changes

Open an issue or a pull request. Keep changes focused, match the surrounding code style, and
describe what you changed and why.

## License

By contributing, you agree that your contributions are released under the project's
[WTFPL](LICENSE) — do what the fuck you want to.
