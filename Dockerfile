# syntax=docker/dockerfile:1.7
# Multi-service all-in-one lightweight container for Render deployment

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
LABEL org.opencontainers.image.title="bank-management-system" \
      org.opencontainers.image.vendor="Bank Management System"

RUN apk add --no-cache curl bash && \
    addgroup -S app && adduser -S -G app -H -s /bin/sh app

WORKDIR /app

# Copy the 6 core microservice jars (discovery-server is eliminated to save ~110MB RAM)
COPY --from=build --chown=app:app /workspace/auth-service/target/auth-service.jar /app/auth-service.jar
COPY --from=build --chown=app:app /workspace/organization-service/target/organization-service.jar /app/organization-service.jar
COPY --from=build --chown=app:app /workspace/attendance-service/target/attendance-service.jar /app/attendance-service.jar
COPY --from=build --chown=app:app /workspace/incentive-service/target/incentive-service.jar /app/incentive-service.jar
COPY --from=build --chown=app:app /workspace/audit-service/target/audit-service.jar /app/audit-service.jar
COPY --from=build --chown=app:app /workspace/api-gateway/target/api-gateway.jar /app/api-gateway.jar

# Copy production configuration & entrypoint script
COPY --chown=app:app .env.production /app/.env
COPY --chown=app:app entrypoint.sh /app/entrypoint.sh

# Normalize CRLF line endings to LF and set executable permissions
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod +x /app/entrypoint.sh

USER app

EXPOSE 10000 8088

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -f -s "http://127.0.0.1:${PORT:-10000}/actuator/health" > /dev/null || exit 1

ENTRYPOINT ["/bin/sh", "/app/entrypoint.sh"]
