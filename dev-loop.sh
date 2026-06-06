#!/usr/bin/env bash
# reddit-crawler dev-loop — exécute direct SANS passer par un LLM (évite le bloquage sur GPU absent)
set -euo pipefail

REPO="/home/kali/projects/reddit-crawler"
echo "=== $(date '+%Y-%m-%d %H:%M:%S') Reddit Crawler Dev Loop Tick ==="

# 1. Build backend Java (skip tests pour speed)
cd "$REPO/backend-java" || { echo "ERROR: Backend dir not found"; exit 1; }
echo "[1/4] Building backend..."
./mvnw clean package -DskipTests -q 2>&1 | tail -3 || echo "Backend build FAILED (exit $?)"

# 2. Build frontend Next.js  
cd "$REPO/frontend"
echo "[2/4] Building frontend..."
npm run build 2>&1 | tail -5 || echo "Frontend build FAILED"

# 3. Clean Docker state (purge old images that may be corrupt)
cd "$REPO"
docker system prune -f 2>/dev/null || true

# 4. Build Docker images (no-cache to avoid stale layers)
echo "[3/4] Building Docker images..."
DOCKER_BUILDKIT=1 docker compose build --no-cache 2>&1 | tail -5 || echo "Docker build FAILED"

# 5. Bring up the full stack with [all] profile
echo "[4/4] Starting stack..."
docker compose up --profile all -d 2>&1 | tail -3

# 6. Verify stack health after 5s
sleep 5
docker compose ps 2>&1 || echo "Some services crashed unexpectedly"

echo "=== Dev loop tick complete ==="
