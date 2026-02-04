# ---- build stage ----
FROM gradle:8.7-jdk17 AS build
WORKDIR /app

# Copy Gradle files first (for caching)
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY gradlew ./
COPY gradle.properties ./

COPY . .

# Ensure wrapper is executable
RUN chmod +x ./gradlew

RUN ./gradlew :server:buildFatJar --no-daemon

# ---- run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/server/build/libs/*-all.jar app.jar
ENV PORT=10000
EXPOSE 10000
CMD ["java", "-jar", "app.jar"]
