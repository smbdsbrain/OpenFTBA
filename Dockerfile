# OpenFTBA self-hosted web server.
#
# Multi-stage: stage 1 builds the Ktor server distribution and the Compose wasm
# web UI from source; stage 2 is a small JRE runtime. Build from the repo root:
#   docker build -t openftba .
# (or just `docker compose up --build` with the example compose file).

# ---- build ----
# Official Gradle image (Gradle + JDK 17 preinstalled) so no Gradle distribution
# download is needed. Dependencies still come from Maven Central, and the wasm
# task downloads a Node toolchain, so this stage needs network access.
FROM gradle:8.13-jdk17 AS build
USER root
WORKDIR /src
COPY . .
RUN gradle --no-daemon :server:installDist :composeApp:wasmJsBrowserDistribution

# ---- runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/server/build/install/server/ /app/
COPY --from=build /src/composeApp/build/dist/wasmJs/productionExecutable/ /app/wasmapp/
RUN chmod +x /app/bin/server

ENV OPENFTBA_PORT=8080 \
    OPENFTBA_WATCH_FOLDER=/data/tracks \
    OPENFTBA_WEBAPP_DIR=/app/wasmapp \
    OPENFTBA_CONFIG_DIR=/data/config \
    OPENFTBA_DEM_FOLDER=/data/dem \
    OPENFTBA_RESCAN_SECONDS=60

EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]
