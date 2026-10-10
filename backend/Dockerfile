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
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig libfreetype6 \n    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/calyvora-backend-*.jar app.jar
# Respect the container's memory limit (free tiers are small).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# The profile is chosen by the deployment, never baked into the image. Each Render service sets
# SPRING_PROFILES_ACTIVE explicitly (prod for production, staging for the demo). Baking in `staging`
# meant a service that forgot to set it would silently run with the dev/seed/mailbox endpoints
# exposed — the worst default for a production box. With nothing baked in, a missing value falls back
# to application.yml's `local`, which cannot reach a real database and so fails loudly instead.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
