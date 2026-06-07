# =============================================================================
# Multi-stage Dockerfile for Spring Boot + Coolify
# Stage 1: Maven build (downloads deps, compiles, packages)
# Stage 2: Minimal JRE runtime
# =============================================================================

# ─── STAGE 1: Build ────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /builder

# Cache Maven deps layer (Docker reuses if pom.xml unchanged)
COPY pom.xml .

# Copy source code
COPY . .

# Compile & package — skip tests for faster CI builds
RUN mvn clean package -DskipTests

# ─── STAGE 2: Production ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS production

WORKDIR /app

# Install curl + tini (healthcheck + Graceful shutdown)
RUN apk add --no-cache curl tini

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy fat JAR from build stage
COPY --from=build /builder/target/reddit-crawler-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080

USER appuser

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["tini", "--"]
CMD ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
