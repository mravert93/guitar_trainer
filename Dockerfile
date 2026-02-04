# ---- build stage ----
FROM gradle:8.7-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle :server:buildFatJar --no-daemon

# ---- run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/server/build/libs/*-all.jar app.jar
ENV PORT=10000
EXPOSE 10000
CMD ["java", "-jar", "app.jar"]
