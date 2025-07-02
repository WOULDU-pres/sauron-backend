# 1. Build Stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew .

# Download dependencies
RUN ./gradlew dependencies || true

COPY src ./src

# Build the application
RUN ./gradlew bootJar

# 2. Final Stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the jar file from the builder stage
COPY --from=builder /app/build/libs/*.jar /app/application.jar

ENTRYPOINT ["java", "-jar", "/app/application.jar"]