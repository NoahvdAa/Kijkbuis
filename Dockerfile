# syntax=docker/dockerfile:1

# Builds the frontend into /build/dist
FROM node:24-slim AS frontend
WORKDIR /build
# The dependencies first, so they're only installed again when they change
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npm run build


# Builds the backend into /build/build/install/backend
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /build
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle ./gradle
COPY backend/src ./src
# The tests need ffmpeg and take a while, so they're left to CI. The libraries come out of Gradle's cache readable for
# root only, while Kijkbuis doesn't run as root.
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon installDist \
    && chmod -R a+rX build/install/backend


# Builds a Java runtime with only the modules Kijkbuis uses, which is less than half the size of a full one. It's built
# on Alpine so it matches the runtime image's C library.
FROM eclipse-temurin:25-jdk-alpine AS java
COPY --from=backend /build/build/install/backend/lib /lib-to-scan
# jdeps finds what the code uses. jdk.charsets has the less common encodings, which an EPG could be in.
RUN modules="$(jdeps --ignore-missing-deps --print-module-deps --multi-release 25 /lib-to-scan/*.jar)" \
    && jlink --add-modules "$modules,jdk.charsets" --strip-debug --no-man-pages --no-header-files --compress zip-6 \
        --output /java


# Runs Kijkbuis, with ffmpeg for the streams. The config and the database live in /data.
FROM alpine:3.24
RUN apk add --no-cache ffmpeg \
    && addgroup -g 1000 kijkbuis \
    && adduser -D -H -u 1000 -G kijkbuis -h /data kijkbuis \
    && mkdir /data \
    && chown kijkbuis:kijkbuis /data

COPY --from=java /java /opt/java
ENV JAVA_HOME=/opt/java
ENV PATH="$JAVA_HOME/bin:$PATH"
COPY --from=backend /build/build/install/backend /app
COPY --from=frontend /build/dist /app/frontend
# Used when the config doesn't set server.frontend-dir
ENV KIJKBUIS_FRONTEND_DIR=/app/frontend

USER kijkbuis
WORKDIR /data
VOLUME /data
EXPOSE 6742
# On the first start, this writes a default config to /data/kijkbuis.toml and stops, so it can be filled in
ENTRYPOINT ["/app/bin/backend", "/data/kijkbuis.toml"]
