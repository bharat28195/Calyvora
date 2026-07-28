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
WORKDIR /app
COPY --from=build /app/target/calyvora-backend-*.jar app.jar
# Respect the container's memory limit (free tiers are small).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENV SPRING_PROFILES_ACTIVE=staging
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
