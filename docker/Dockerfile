# =============================================================================
# CareerPilot AI — backend image
#
# Multi-stage: the build stage carries Maven, the JDK, and the whole dependency
# cache; the runtime stage carries a JRE and one jar. The final image is a
# fraction of the size, and — more importantly — ships no compiler and no build
# tooling for an attacker to use if they get a shell.
#
# Railway builds with Nixpacks by default (see railway.json), so this Dockerfile
# is used for local container testing and as the migration path if the build
# ever moves to a Dockerfile-based platform. Keeping the two in sync matters:
# "works on Railway, fails in Docker" is a difficult afternoon.
#
# Build from the repository root:
#     docker build -f docker/Dockerfile -t careerpilot-api .
# =============================================================================

# --- Build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /build

# Copy the POM alone first, then resolve dependencies.
#
# This is the layer-caching trick that matters: dependencies change rarely,
# source changes constantly. Copying everything at once would re-download the
# entire dependency tree on every source edit, turning a 20-second rebuild into
# a 4-minute one.
COPY backend/pom.xml .
RUN mvn -B dependency:go-offline

# Now the source, which invalidates only the layers below this line.
COPY backend/src ./src

# Tests do NOT run here. They need a Docker daemon for Testcontainers, and
# running Docker inside a Docker build is a mess best avoided. CI runs the full
# suite before anything reaches this file — see .github/workflows/backend-ci.yml.
RUN mvn -B clean package -DskipTests

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Run as a non-root user.
#
# A container running as root turns any remote-code-execution bug into a
# container escape attempt rather than a contained incident. This costs two
# lines and removes an entire escalation step.
RUN addgroup --system --gid 1001 careerpilot \
 && adduser  --system --uid 1001 --ingroup careerpilot careerpilot

WORKDIR /app

COPY --from=build --chown=careerpilot:careerpilot /build/target/*.jar app.jar

USER careerpilot

EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx.
#
# The JVM's default heap sizing predates containers and reads the host's memory,
# not the cgroup limit — so on a 512 MB container it can size a heap for a 16 GB
# machine and get OOM-killed with no Java stack trace, which is a genuinely
# confusing failure. This lets the heap scale with whatever the platform grants.
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseContainerSupport", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
