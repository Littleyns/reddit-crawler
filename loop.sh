#!/bin/bash
while true; do
    ts=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$ts] >>> START tick"
    cd /home/kali/projects/reddit-crawler/backend-java && ./mvnw clean package -DskipTests -q 2>&1 | tail -3 || echo "Backend build FAILED"
    cd /home/kali/projects/reddit-crawler/frontend && npm run build 2>&1 | tail -3 || echo "Frontend build FAILED"
    cd /home/kali/projects/reddit-crawler
    docker compose build --no-cache 2>&1 | tail -3 || true
    docker compose up --profile all -d 2>&1 || true
    docker compose ps && touch /tmp/dev-loop-healthy
    echo "[$ts] <<< COMPLETE"
    sleep 60
done
