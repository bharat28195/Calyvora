# Backend image — build with Maven (JDK 21), run on a slim JRE.
# Build context is the `backend/` directory (see render.yaml / docs/DEPLOY.md).

# ---- build stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Tests use an embedded Postgres binary that isn't declared for Linux — skip them in the image.
# CI on the dev machine runs the full suite; the image just packages the jar.
RUN mvn -B clean package -DskipTests

# ---- run stage ----
FROM eclipse-temurin:21-jre
# Letters are rendered to PDF and letterpads from PDF to images on the server (PD-64); Java2D needs
# fontconfig and FreeType to do that headless.
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig libfreetype6 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/calyvora-backend-*.jar app.jar
# Fit the whole JVM, not just its heap, inside the container (free tier and Starter are both 512 MB).
#
# D1: opening a letter to sign got the process killed by the host (a 502, then minutes of 503s). A
# heap shortage would only have failed that request with a 500; a kill means the process as a whole
# went over the container's memory. Measured with native memory tracking (2026-11-08): about 220 MB
# lives outside the heap — class metadata ~140 MB, compiled code ~50 MB, symbols ~40 MB — and rendering
# letters grows the heap to its cap, where it stays. A 75% heap (384 MB) put the total near 650 MB and
# a 60% heap still near 550 MB. Hence:
#   - heap 45% of the container (~230 MB; letters, payroll and the read sweep all ran within it)
#   - the serial collector, which a small container would pick anyway, made explicit: it has almost
#     no memory of its own, where G1 used ~75 MB in the same measurement
#   - compiled code capped at 96 MB (it used ~50; when full the JIT stops compiling, nothing fails)
#   - two malloc arenas: glibc otherwise keeps one per thread, and that memory is invisible to Java
#     yet counted against the container
# If the plan grows past 512 MB these percentages grow with it.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=45 -XX:+UseSerialGC -XX:ReservedCodeCacheSize=96m"
ENV MALLOC_ARENA_MAX=2
# The profile is chosen by the deployment, never baked into the image. Each Render service sets
# SPRING_PROFILES_ACTIVE explicitly (prod for production, staging for the demo). Baking in `staging`
# meant a service that forgot to set it would silently run with the dev/seed/mailbox endpoints
# exposed — the worst default for a production box. With nothing baked in, a missing value falls back
# to application.yml's `local`, which cannot reach a real database and so fails loudly instead.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
