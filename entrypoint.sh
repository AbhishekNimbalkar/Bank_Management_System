#!/bin/sh
set -e

# Forward termination signals to all child processes
trap "kill 0" EXIT TERM INT

# Default environment variables
export EUREKA_CLIENT_ENABLED="false"
export AUTH_SERVICE_URL="http://localhost:8081"
export ORGANIZATION_SERVICE_URL="http://localhost:8082"
export ATTENDANCE_SERVICE_URL="http://localhost:8083"
export INCENTIVE_SERVICE_URL="http://localhost:8085"
export AUDIT_SERVICE_URL="http://localhost:8084"
export JWT_JWK_SET_URI="${JWT_JWK_SET_URI:-http://localhost:8081/.well-known/jwks.json}"
export GATEWAY_ONLY="${GATEWAY_ONLY:-false}"
export JWT_ALLOW_EPHEMERAL_KEY="${JWT_ALLOW_EPHEMERAL_KEY:-true}"
export SWAGGER_ENABLED="${SWAGGER_ENABLED:-false}"
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://localhost:5173,https://hrms.nexvitait.com,*}"
export DB_POOL_SIZE="${DB_POOL_SIZE:-3}"

# Highly optimized JVM flags for Render 512MB RAM limit:
# 1. -XX:TieredStopAtLevel=1 disables heavy C2 JIT compilation (cuts memory & CPU by >50%)
# 2. -XX:+UseSerialGC avoids multithreaded GC native buffer overhead
# 3. -Xss256k reduces per-thread stack memory
# 4. -XX:ReservedCodeCacheSize=12m caps JIT code cache
# 5. -XX:MaxMetaspaceSize=40m caps metaspace per JVM
JVM_COMMON="-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xss256k -XX:ReservedCodeCacheSize=12m -XX:MaxMetaspaceSize=40m -Djava.security.egd=file:/dev/urandom -Duser.timezone=UTC"

echo "=========================================================="
echo " Starting Bank Management System (Lightweight Container) "
echo "=========================================================="

start_service() {
  NAME=$1
  PORT_NUM=$2
  JAR=$3
  XMS=$4
  XMX=$5

  echo "-> Starting $NAME on port $PORT_NUM (Heap: $XMX)..."
  java $JVM_COMMON -Xms${XMS} -Xmx${XMX} -jar /app/${JAR} --server.port=${PORT_NUM} &

  echo "   Waiting for $NAME to become ready on port $PORT_NUM..."
  for i in $(seq 1 45); do
    if curl -s -f http://127.0.0.1:${PORT_NUM}/actuator/health >/dev/null 2>&1 || curl -s http://127.0.0.1:${PORT_NUM} >/dev/null 2>&1; then
      echo "   $NAME is ready!"
      break
    fi
    sleep 1
  done
}

# 1. Auth Service (8081) - must be up first for JWKS public keys
start_service "auth-service" 8081 "auth-service.jar" "16m" "32m"

# Verify JWKS endpoint
echo "   Verifying JWKS endpoint at http://127.0.0.1:8081/.well-known/jwks.json..."
for i in $(seq 1 30); do
  if curl -s -f http://127.0.0.1:8081/.well-known/jwks.json >/dev/null 2>&1; then
    echo "   JWKS keys are ready!"
    break
  fi
  sleep 1
done

# 2. Organization Service (8082)
start_service "organization-service" 8082 "organization-service.jar" "16m" "32m"

# 3. Attendance Service (8083)
start_service "attendance-service" 8083 "attendance-service.jar" "16m" "28m"

# 4. Incentive Service (8085)
start_service "incentive-service" 8085 "incentive-service.jar" "16m" "28m"

# 5. Audit Service (8084)
start_service "audit-service" 8084 "audit-service.jar" "16m" "24m"

# 6. API Gateway on Render dynamic PORT (Foreground)
GATEWAY_PORT="${PORT:-10000}"
echo "-> Starting api-gateway on port ${GATEWAY_PORT} (Foreground)..."
java $JVM_COMMON -Xms16m -Xmx32m -jar /app/api-gateway.jar --server.port=${GATEWAY_PORT}
