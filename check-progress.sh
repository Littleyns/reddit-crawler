#!/bin/bash
# Reddit Crawler Development Monitor
# Run every 1-2 hours to check overnight development progress

LOG_FILE="/home/kali/.openclaw/workspace/reddit-crawler/monitoring.log"
REPO_DIR="/home/kali/.openclaw/workspace/reddit-crawler"
BACKEND_DIR="/home/kali/.openclaw/workspace/backend-dev"
FRONTEND_DIR="/home/kali/.openclaw/workspace/fullstack-dev"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================" >> $LOG_FILE
echo "$(date -Iseconds) - CHECK START" >> $LOG_FILE

# 1. Git Repository Status
echo "📦 Git Repository Status:" >> $LOG_FILE
cd $REPO_DIR
git log --oneline -3 >> $LOG_FILE 2>&1
echo "" >> $LOG_FILE

# 2. Backend Agent Health
echo "🔧 Backend Agent ($BACKEND_DIR):" >> $LOG_FILE
ls -la $BACKEND_DIR/src/ >> $LOG_FILE 2>&1
echo "Files in src/: $(find $BACKEND_DIR/src -name '*.py' | wc -l)" >> $LOG_FILE
ls -la $BACKEND_DIR/tests/ >> $LOG_FILE 2>&1
echo "Test files: $(find $BACKEND_DIR/tests -name '*.py' | wc -l)" >> $LOG_FILE
echo "" >> $LOG_FILE

# 3. Frontend Agent Health
echo "🎨 Frontend Agent ($FRONTEND_DIR):" >> $LOG_FILE
ls -la $FRONTEND_DIR/frontend/ >> $LOG_FILE 2>&1
echo "Pages: $(find $FRONTEND_DIR/frontend/pages -name '*.tsx' 2>/dev/null | wc -l)" >> $LOG_FILE
echo "Components: $(find $FRONTEND_DIR/frontend/components -name '*.tsx' 2>/dev/null | wc -l)" >> $LOG_FILE
echo "" >> $LOG_FILE

# 4. GitHub Status
echo "🌐 GitHub Status:" >> $LOG_FILE
gh repo view ArabTooling/reddit-crawler --json pushable,defaultBranchRef >> $LOG_FILE 2>&1
echo "" >> $LOG_FILE

# Summary
echo "=========================================" >> $LOG_FILE
echo "$(date -Iseconds) - CHECK END" >> $LOG_FILE

echo -e "${GREEN}✅ Monitoring check complete. Log saved to $LOG_FILE${NC}"
cat $LOG_FILE | tail -50
