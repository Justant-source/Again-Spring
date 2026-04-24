#!/bin/bash

set -e

# ============================================================
# Entrypoint Script for Again Spring Backend
# Validates environment and starts the application
# ============================================================

echo "=========================================="
echo "Again Spring Backend Startup"
echo "=========================================="
echo ""

# ============================================================
# 1. Validate Required Environment Variables
# ============================================================

REQUIRED_VARS=(
    "MONGO_URI"
    "NEO4J_URI"
    "JWT_SECRET"
)

echo "Checking required environment variables..."
for var in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!var}" ]; then
        echo "ERROR: Required environment variable not set: $var"
        echo ""
        echo "Required variables:"
        echo "  MONGO_URI      - MongoDB connection string (e.g., mongodb://user:pass@host:27017/db)"
        echo "  NEO4J_URI      - Neo4j connection string (e.g., bolt://host:7687)"
        echo "  JWT_SECRET     - JWT signing secret (set to secure value in production)"
        echo ""
        echo "Optional variables:"
        echo "  NEO4J_USER     - Neo4j username (default: neo4j)"
        echo "  NEO4J_PASSWORD - Neo4j password (required if NEO4J_USER set)"
        echo "  LLM_PROVIDER   - LLM provider: 'claude-code' or 'claude-api' (default: claude-code)"
        echo "  ANTHROPIC_API_KEY - API key for claude-api provider"
        echo "  CLAUDE_LOGIN_STATUS - Set to 'ok' if Claude CLI is pre-authenticated"
        echo "  SPRING_PROFILES_ACTIVE - Active profiles (default: prod)"
        echo "  JAVA_OPTS      - JVM options (default: -Xms512m -Xmx1024m)"
        echo ""
        exit 1
    fi
done

echo "✓ All required environment variables are set"
echo ""

# ============================================================
# 2. Validate Claude CLI (if using claude-code provider)
# ============================================================

LLM_PROVIDER="${LLM_PROVIDER:-claude-code}"
if [ "$LLM_PROVIDER" = "claude-code" ]; then
    echo "Checking Claude CLI installation..."

    if ! command -v claude &> /dev/null; then
        echo "ERROR: claude CLI not found in PATH"
        echo "Install: npm install -g @anthropic-ai/claude-code"
        exit 1
    fi

    CLAUDE_VERSION=$(claude --version 2>&1 || true)
    if [ -z "$CLAUDE_VERSION" ]; then
        echo "ERROR: claude --version failed"
        exit 1
    fi

    echo "✓ Claude CLI available: $CLAUDE_VERSION"
    echo ""

    # Optional: Check authentication status
    if [ -z "$CLAUDE_LOGIN_STATUS" ]; then
        echo "Note: CLAUDE_LOGIN_STATUS not set. Assuming Claude CLI is authenticated."
        echo "If not authenticated, run: claude login"
    fi
fi

# ============================================================
# 3. Log Configuration (for debugging)
# ============================================================

echo "Configuration:"
echo "  Spring Profiles: $SPRING_PROFILES_ACTIVE"
echo "  LLM Provider: $LLM_PROVIDER"
echo "  MongoDB: ${MONGO_URI:0:50}..."
echo "  Neo4j: ${NEO4J_URI:0:50}..."
echo "  Java Opts: $JAVA_OPTS"
echo ""

# ============================================================
# 4. Start Application
# ============================================================

echo "Starting Again Spring backend..."
echo "=========================================="
echo ""

exec java $JAVA_OPTS \
    -Dspring.profiles.active="$SPRING_PROFILES_ACTIVE" \
    -Dspring.data.mongodb.uri="$MONGO_URI" \
    -Dspring.data.neo4j.uri="$NEO4J_URI" \
    ${NEO4J_USER:+-Dspring.data.neo4j.authentication.username="$NEO4J_USER"} \
    ${NEO4J_PASSWORD:+-Dspring.data.neo4j.authentication.password="$NEO4J_PASSWORD"} \
    -Djwt.secret="$JWT_SECRET" \
    ${ANTHROPIC_API_KEY:+-Dllm.claude-api.key="$ANTHROPIC_API_KEY"} \
    -Dllm.provider="$LLM_PROVIDER" \
    -jar /app/app.jar
