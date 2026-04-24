#!/bin/bash

# Export OpenAPI specification from running backend to shared schemas
# Usage: ./backend/scripts/export-openapi.sh
# Requires: backend running on http://localhost:8080

set -e

BACKEND_HOST="${BACKEND_HOST:-http://localhost:8080}"
OUTPUT_FILE="${1:-./../shared/schemas/openapi.yaml}"

echo "Exporting OpenAPI spec from $BACKEND_HOST..."

# Check if backend is running
if ! curl -sf "$BACKEND_HOST/actuator/health" > /dev/null; then
    echo "ERROR: Backend not responding at $BACKEND_HOST"
    echo "Start backend with: ./gradlew bootRun"
    exit 1
fi

# Export OpenAPI YAML
echo "Fetching OpenAPI spec..."
curl -sf "$BACKEND_HOST/v3/api-docs.yaml" -o "$OUTPUT_FILE"

if [ -f "$OUTPUT_FILE" ]; then
    echo "Success! OpenAPI spec exported to: $OUTPUT_FILE"
    wc -l "$OUTPUT_FILE"
else
    echo "ERROR: Failed to export OpenAPI spec"
    exit 1
fi
