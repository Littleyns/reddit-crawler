# ==============================================================================
# Backend Dockerfile — copy pre-compiled JAR, no build tools container needed
# Compile locally (Jenkins/GitHub Actions/dev machine) → docker cp or COPY the JAR.
# This approach sidesteps slow Maven image pulls / mvnw + curl on Alpine.
# ==============================================================================

FROM eclipse-temurin:21-jre-alpine AS production

WORKDIR /app

# Add healthcheck tools so we can probe the Spring Boot /actuator endpoint
RUN apk add --no-cache curl tini

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy pre-built Spring Boot JAR produced by ./mvnw package
COPY target/reddit-crawler-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080

# Healthcheck — waits for Spring to start (up to 90s via --start-period)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
  CMD curl -f http://localhost:8080/api/config/test || exit 1

USER appuser
ENTRYPOINT ["tini", "--"]
CMD ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
