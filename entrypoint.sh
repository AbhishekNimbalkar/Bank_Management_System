#!/bin/sh
set -e

# Forward termination signals to all processes in this process group
trap "kill 0" EXIT TERM INT

# Default environment variables
export EUREKA_USERNAME="${EUREKA_USERNAME:-eureka}"
export EUREKA_PASSWORD="${EUREKA_PASSWORD:-eureka}"
export EUREKA_URL="http://${EUREKA_USERNAME}:${EUREKA_PASSWORD}@localhost:8761/eureka/"
export JWT_JWK_SET_URI="${JWT_JWK_SET_URI:-http://localhost:8081/.well-known/jwks.json}"
export GATEWAY_ONLY="${GATEWAY_ONLY:-false}"
export JWT_ALLOW_EPHEMERAL_KEY="${JWT_ALLOW_EPHEMERAL_KEY:-true}"
export SWAGGER_ENABLED="${SWAGGER_ENABLED:-true}"
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://localhost:5173,https://hrms.nexvitait.com,*}"

# Highly optimized JVM flags for Render Free/Starter tier (512MB RAM total):
# 1. -XX:TieredStopAtLevel=1 disables heavy C2 JIT compilation, reducing CPU & memory by >50% and speeding up boot
# 2. -XX:+UseSerialGC avoids native multithreaded GC buffer overhead
# 3. -Xss256k reduces stack memory allocation per thread from 1MB down to 256KB
# 4. -XX:ReservedCodeCacheSize & -XX:MaxMetaspaceSize cap native memory pools
JVM_COMMON="-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xss256k -XX:ReservedCodeCacheSize=24m -XX:MaxMetaspaceSize=60m -Djava.security.egd=file:/dev/urandom -Duser.timezone=UTC"

echo "=========================================================="
echo " Starting Bank Management System (All-In-One Container) "
echo "=========================================================="

echo "-> [1/7] Starting Discovery Server (Eureka on port 8761)..."
java $JVM_COMMON -Xms24m -Xmx40m -jar /app/discovery-server.jar --server.port=8761 &

echo "Waiting for Eureka Server to respond on port 8761..."
for i in $(seq 1 30); do
  if curl -s -f http://127.0.0.1:8761/actuator/health >/dev/null 2>&1 || curl -s http://127.0.0.1:8761 >/dev/null 2>&1; then
    echo "Discovery Server is up and ready!"
    break
  fi
  sleep 1
done

echo "-> [2/7] Starting Auth Service (port 8081)..."
java $JVM_COMMON -Xms32m -Xmx64m -jar /app/auth-service.jar --server.port=8081 &

echo "-> [3/7] Starting Organization Service (port 8082)..."
java $JVM_COMMON -Xms32m -Xmx64m -jar /app/organization-service.jar --server.port=8082 &

echo "-> [4/7] Starting Attendance Service (port 8083)..."
java $JVM_COMMON -Xms32m -Xmx56m -jar /app/attendance-service.jar --server.port=8083 &

echo "-> [5/7] Starting Incentive Service (port 8085)..."
java $JVM_COMMON -Xms32m -Xmx56m -jar /app/incentive-service.jar --server.port=8085 &

echo "-> [6/7] Starting Audit Service (port 8084)..."
java $JVM_COMMON -Xms32m -Xmx56m -jar /app/audit-service.jar --server.port=8084 &

# Ensure Auth Service JWKS public keys are exposed before API Gateway boots
echo "Waiting for Auth Service JWKS endpoint (http://localhost:8081/.well-known/jwks.json)..."
for i in $(seq 1 45); do
  if curl -s -f http://127.0.0.1:8081/.well-known/jwks.json >/dev/null 2>&1; then
    echo "Auth Service JWKS keys are ready!"
    break
  fi
  sleep 1
done

# The Gateway binds to Render's dynamic external port (default 10000 or 8088)
GATEWAY_PORT="${PORT:-10000}"
echo "-> [7/7] Starting API Gateway on port ${GATEWAY_PORT} (Foreground)..."
java $JVM_COMMON -Xms32m -Xmx64m -jar /app/api-gateway.jar --server.port=${GATEWAY_PORT}
