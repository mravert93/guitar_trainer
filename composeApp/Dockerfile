# ---- build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy only what's needed for Gradle
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY composeApp ./composeApp

RUN chmod +x gradlew
RUN ./gradlew :composeApp:jsBrowserProductionWebpack

# ---- run stage ----
FROM nginx:alpine
COPY --from=build /app/composeApp/build/dist/js/productionExecutable /usr/share/nginx/html
COPY composeApp/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
