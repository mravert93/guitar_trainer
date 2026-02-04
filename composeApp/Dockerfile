# ---- build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy wrapper + gradle config first (caching)
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle.properties ./
# If you have version catalogs:
COPY gradle/libs.versions.toml gradle/libs.versions.toml

RUN chmod +x gradlew

# ✅ Now copy the modules that are needed for the JS build
COPY shared ./shared
COPY composeApp ./composeApp

# Build web bundle
RUN ./gradlew :composeApp:jsBrowserProductionWebpack --no-daemon

RUN ./gradlew :composeApp:jsBrowserProductionWebpack --no-daemon && \
    echo "---- dist tree ----" && \
    find /app/composeApp/build -maxdepth 6 -type f \( -name "index.html" -o -name "*.js" \) -print


# ---- run stage ----
FROM nginx:alpine
COPY --from=build /app/composeApp/build/dist/js/productionExecutable /usr/share/nginx/html
COPY composeApp/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
