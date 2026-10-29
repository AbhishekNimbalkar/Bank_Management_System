# syntax=docker/dockerfile:1.7
# One Dockerfile for every service:  docker build --build-arg MODULE=auth-service -t bankmanagement/auth-service .

FROM maven:3.9.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
ARG MODULE
LABEL org.opencontainers.image.title="bankmanagement-${MODULE}" \
      org.opencontainers.image.vendor="Bank Management System"

# Run as an unprivileged user; the image contains only the JRE and the application jar.
RUN addgroup -S app && adduser -S -G app -H -s /sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/${MODULE}/target/${MODULE}.jar /app/app.jar
USER app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom -Duser.timezone=UTC"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- "http://127.0.0.1:${PORT}/actuator/health/liveness" > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
