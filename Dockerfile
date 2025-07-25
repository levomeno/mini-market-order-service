# Build stage - use official Maven image for better optimization
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom.xml first for better layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build with optimizations
COPY src ./src
RUN mvn clean package -DskipTests -B -Dspring-boot.repackage.skip=false

# Runtime stage - Use JRE Alpine (smallest JRE)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl and create user in single layer to reduce size
RUN apk add --no-cache curl && \
    addgroup -S appuser && \
    adduser -S -G appuser appuser && \
    rm -rf /var/cache/apk/*

# Copy JAR file
COPY --from=build --chown=appuser:appuser /app/target/*.jar app.jar

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run with container-optimized JVM settings
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-XX:+UseStringDeduplication", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]