# ============================================================================
# Multi-stage Dockerfile for Java Services (decision-server / decision-admin)
# ============================================================================
# Build:
#   docker build --build-arg SERVICE_NAME=decision-server -t finance-decision-server .
#   docker build --build-arg SERVICE_NAME=decision-admin -t finance-decision-admin .
# ============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Build with Maven + JDK 17
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder

ARG SERVICE_NAME
ARG MAVEN_PROFILE=prod
ARG MAVEN_OPTS="-Xms256m -Xmx1024m"

WORKDIR /build

# Layer 1: Resolve dependencies (cached unless pom.xml changes)
COPY pom.xml .
COPY engine-common/pom.xml engine-common/pom.xml
COPY engine-core/pom.xml engine-core/pom.xml
COPY engine-test/pom.xml engine-test/pom.xml
COPY decision-admin/pom.xml decision-admin/pom.xml
COPY decision-sdk/pom.xml decision-sdk/pom.xml
COPY decision-server/pom.xml decision-server/pom.xml
COPY data-platform/pom.xml data-platform/pom.xml
COPY data-platform/flink-jobs/pom.xml data-platform/flink-jobs/pom.xml
COPY data-platform/data-service/pom.xml data-platform/data-service/pom.xml
COPY data-platform/data-governance/pom.xml data-platform/data-governance/pom.xml

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -B --fail-never

# Layer 2: Build source
COPY . .

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn package \
      -B \
      -P${MAVEN_PROFILE} \
      -DskipTests \
      -pl ${SERVICE_NAME} \
      -am \
    && cp ${SERVICE_NAME}/target/*.jar /app.jar

# ---------------------------------------------------------------------------
# Stage 2: Runtime with JRE 17 (minimal image)
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

ARG SERVICE_NAME
ARG SERVICE_PORT=8080

LABEL maintainer="finance-bigdata-team"
LABEL service=${SERVICE_NAME}
LABEL description="Finance Bigdata Platform - ${SERVICE_NAME}"

# Install curl for health check (must be before USER switch)
RUN apk add --no-cache curl

# Add a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy the fat jar from builder
COPY --from=builder /app.jar app.jar

# Create log directory
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose service port
EXPOSE ${SERVICE_PORT}

HEALTHCHECK --interval=15s --timeout=5s --retries=3 --start-period=60s \
    CMD curl -sf http://localhost:${SERVICE_PORT}/actuator/health || exit 1

# JVM tuning: G1GC, container-aware memory, OOM dump
ENV JAVA_OPTS="-XX:+UseG1GC \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/app/logs/heapdump.hprof \
  -Dfile.encoding=UTF-8 \
  -Duser.timezone=Asia/Shanghai"

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/app.jar"]
