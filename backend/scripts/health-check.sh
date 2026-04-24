#!/bin/bash

# Health Check Script for Again Spring Backend
# Used by deployment orchestration (K8s, Docker Compose, etc.)

set -e

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
TIMEOUT="${TIMEOUT:-5}"

# ============================================================
# Check Actuator Health
# ============================================================

echo "Checking backend health at $BACKEND_URL..."

# Try /actuator/health
if curl -sf --max-time "$TIMEOUT" "$BACKEND_URL/actuator/health" > /dev/null 2>&1; then
    HEALTH_STATUS=$(curl -s "$BACKEND_URL/actuator/health" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    if [ "$HEALTH_STATUS" = "UP" ]; then
        echo "✓ Backend healthy: $HEALTH_STATUS"
    else
        echo "⚠ Backend degraded: $HEALTH_STATUS"
        exit 1
    fi
else
    echo "✗ Backend not responding"
    exit 1
fi

# ============================================================
# Check API Health Endpoint
# ============================================================

if curl -sf --max-time "$TIMEOUT" "$BACKEND_URL/api/health" > /dev/null 2>&1; then
    echo "✓ API health endpoint responding"
else
    echo "⚠ API health endpoint not responding (may not be implemented yet)"
    # Non-fatal for MVP
fi

echo ""
echo "Health check passed"
exit 0
