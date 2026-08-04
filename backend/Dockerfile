# =============================================================================
# CareerPilot AI — backend image
#
# BUILD CONTEXT IS backend/, not the repository root. That is why this file
# lives here rather than in docker/: Railway's "Root Directory" for this service
# is set to `backend`, and the build context follows it. A Dockerfile that said
# `COPY backend/pom.xml` would fail on Railway with "file not found", because
# from inside that context there is no backend/ directory.
#
#     docker build -t careerpilot-api backend/
#
# Multi-stage: the build stage carries Maven, the full JDK, and the dependency
# cache; the runtime stage carries a JRE and one jar. The final image is a
# fraction of the size and — more importantly — ships no compiler and no build
# tooling for an attacker to use if they ever get a shell.
# =============================================================================

# --- Build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /build

# Copy the POM alone first, then resolve dependencies.
#
# This is the layer-caching trick that matters: dependencies change rarely,
# source changes constantly. Copying everything at once would re-download the
# entire dependency tree on every source edit, turning a 30-second rebuild into
# several minutes — on every deploy.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

# Now the source, which invalidates only the layers below this line.
COPY src ./src

# Tests do NOT run here.
#
# The integration tests need a Docker daemon for Testcontainers, and running
# Docker inside a Docker build is a mess best avoided. CI runs the full suite on
# every push before anything reaches a deploy — see .github/workflows/backend-ci.yml.
# A build that skipped tests AND had no CI would be indefensible; this one has CI.
RUN mvn -B -ntp clean package -DskipTests

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Run as a non-root user.
#
# A container running as root turns any remote-code-execution bug into a
# container-escape attempt rather than a contained incident. Two lines, one
# entire escalation step removed.
RUN addgroup --system --gid 1001 careerpilot \
 && adduser  --system --uid 1001 --ingroup careerpilot careerpilot

WORKDIR /app

# Wildcard rather than the exact filename, so a version bump in pom.xml does not
# silently break the image with "no such file".
COPY --from=build --chown=careerpilot:careerpilot /build/target/*.jar app.jar

USER careerpilot

# Documentation only. Railway injects PORT at runtime and the application binds
# to it (server.port=${PORT:8080}); EXPOSE does not publish or restrict anything.
EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx.
#
# The JVM's default heap sizing predates containers and reads the HOST's memory,
# not the cgroup limit. On a 512 MB container it can size a heap for a 16 GB
# machine and get OOM-killed by the kernel with no Java stack trace at all —
# the container simply disappears, which is a genuinely baffling first
# deployment. This lets the heap scale with whatever the platform actually grants.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseContainerSupport", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
