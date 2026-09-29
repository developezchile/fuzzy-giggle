# Build stage: compile + shade the fat jar with Maven on JDK 25
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /build

# Cache dependencies separately from source so `mvn package` doesn't re-download on every
# source-only change.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# Runtime stage: JRE-only, no Maven/JDK toolchain, non-root user
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p logs && chown app:app logs
USER app

COPY --from=build /build/target/viajes-eventos-api.jar ./viajes-eventos-api.jar

# Real secrets (DB_PASSWORD, JWT_SECRET, SMTP_PASSWORD, ...) must be passed as runtime env vars /
# a mounted CONFIG_FILE — never baked into the image. See config.yml's header comment and
# ConfigFile's javadoc for the full lookup order; the jar already ships safe defaults from
# src/main/resources/config.yml.
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "viajes-eventos-api.jar"]
