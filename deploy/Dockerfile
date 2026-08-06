# Self-contained build: needs only Docker on the host, no local JDK, Maven or Node.
# The repository root is the build context.

# 1. Frontend. Built separately so the Maven stage can skip its npm invocation and
#    so a backend-only change does not reinstall node modules.
FROM node:22-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/tsconfig.json frontend/vite.config.ts frontend/index.html ./
COPY frontend/src ./src
RUN npm run build

# 2. Backend. exec.skip stops the pom from running npm again; the resources plugin
#    copies ../frontend/dist into the jar, so the built assets are staged there first.
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
COPY backend/pom.xml ./backend/pom.xml
RUN mvn -pl backend -am -q -B dependency:go-offline -Dexec.skip=true || true
COPY backend ./backend
COPY --from=frontend /build/frontend/dist ./frontend/dist
RUN mvn -pl backend -B clean package -DskipTests -Dexec.skip=true

# 3. Runtime.
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl is used by the compose healthcheck.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=backend /build/backend/target/quarkus-app/lib/ ./lib/
COPY --from=backend /build/backend/target/quarkus-app/*.jar ./
COPY --from=backend /build/backend/target/quarkus-app/app/ ./app/
COPY --from=backend /build/backend/target/quarkus-app/quarkus/ ./quarkus/

# Run unprivileged.
RUN useradd --system --uid 1001 --create-home yac4e && chown -R yac4e:yac4e /app
USER yac4e

ENV HTTP_PORT=8080
EXPOSE 8080
CMD ["java", "-jar", "quarkus-run.jar"]
