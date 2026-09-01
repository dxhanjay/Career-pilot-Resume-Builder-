# =============================================================================
# CareerPilot AI — one image, one service.
#
# BUILD CONTEXT IS THE REPOSITORY ROOT, because this image needs both halves of
# the monorepo: the React client is compiled and then baked into the Spring Boot
# jar as static resources.
#
#     docker build -t careerpilot .
#
# Why one image rather than two services:
#
#   - One origin. No CORS preflight, no API base URL to configure, no
#     environment variable that is wrong in exactly one environment.
#   - One deploy. There is never a window where the client is newer than the API
#     it is calling, which is the failure mode of split frontend deploys.
#   - One thing to pay for and one thing to keep awake.
#
# The cost is that a CSS change rebuilds the jar. For a project of this size
# that is a minute, and it buys away an entire category of production bug.
# =============================================================================

# --- Stage 1: the client -----------------------------------------------------
FROM node:22-alpine AS client

WORKDIR /web

# Manifests first, so a source edit does not re-resolve the dependency tree.
COPY frontend/package.json frontend/package-lock.json ./

# `npm ci` rather than `npm install`: it installs exactly what the lockfile
# pins and fails if the two have drifted. A production image should never
# silently resolve a different dependency graph than the one that was tested.
RUN npm ci --no-audit --no-fund

COPY frontend/ ./
RUN npm run build

# --- Stage 2: the server -----------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /build

COPY backend/pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY backend/src ./src

# The built client becomes classpath:/static, which SpaConfig serves and falls
# back to for client-side routes. It is copied after the source so a frontend
# change does not invalidate the Maven dependency layer.
COPY --from=client /web/dist ./src/main/resources/static

# Tests do NOT run here. The integration suite needs a Docker daemon for
# Testcontainers, and Docker-in-Docker during an image build is a mess worth
# avoiding. CI runs the full suite on every push — see
# .github/workflows/backend-ci.yml. A build that skipped tests and had no CI
# would be indefensible; this one has CI.
RUN mvn -B -ntp clean package -DskipTests

# --- Stage 3: runtime --------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# A container running as root turns any remote-code-execution bug into a
# container-escape attempt rather than a contained incident.
RUN addgroup --system --gid 1001 careerpilot \
 && adduser  --system --uid 1001 --ingroup careerpilot careerpilot

WORKDIR /app

COPY --from=build --chown=careerpilot:careerpilot /build/target/*.jar app.jar

USER careerpilot

# Documentation only. Railway injects PORT at runtime and the application binds
# to it (server.port=${PORT:8080}); EXPOSE neither publishes nor restricts.
EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx.
#
# The JVM's default heap sizing predates containers and reads the HOST's memory
# rather than the cgroup limit. On a small container it can size a heap for a
# 16 GB machine and be OOM-killed by the kernel with no Java stack trace at all
# — the container simply disappears, which is a genuinely baffling first
# deployment.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseContainerSupport", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
