# Multi-stage build for Reddit Crawler API
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven project files
COPY pom.xml ./
COPY src ./src
COPY mvnw ./

# Run Maven build
RUN apk add --no-cache bash curl && \
    chmod +x ./mvnw && \
    ./mvnw clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy built JAR from builder
COPY --from=builder /build/target/reddit-crawler-1.0.0-SNAPSHOT.jar app.jar

# Copy migrations
COPY src/main/resources/db ./db

# Expose port
EXPOSE 8080

# Run as non-root user
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
