[Content of task.md]
[Content of dev-factory-template.md]
# Reddit Crawler — Dev Factory Autonomous Loop (Hermes Official Pattern)

Ce fichier configure un pipeline de développement autonome 24/7 conforme au pattern `pm-orchestrator` de Hermes Agent :
- **1x PM Orchestrator** → gère le backlog, assigne les tâches aux workers
- **Nx Developer Workers** → codent en continu (backend Java + frontend Next.js)
- Boucle continue sans cron gaps pour maintenir le GPU actif

## Architecture

```
┌─────────────────────────────────────────────┐
│           Dev Factory Loop (24/7)            │
│                                              │
│  PM Orchestrator (every 30s)                 │
│     ├── Read BACKLOG.md                      │
│     ├── Assign tasks via delegate_task       │
│     ├── Monitor worker builds                │
│     └── Docker deploy & health checker       │
│                                              │
│  Dev Workers (parallel, continuous):          │
│     ├── Backend Worker → Spring Boot         │
│     ├── Frontend Worker → NextJS             │
│     └── QA Worker → Tests integration        │
└─────────────────────────────────────────────┘
```

## Setup

### 1. Cron PM Orchestrator (admin-dev-factory)
- **Schedule:** every 30s (pour éviter les GPU gaps)
- **Job ID:** admin-dev-factory
- **Prompt:** voir ci-dessous
- **Skills:** pm-orchestrator + deploy-pipeline-weekly

### 2. Backend Worker Cron (dev-looper-backend)
- **Schedule:** every 1m
- **Skill:** unified-po-dev-cron
- **Model:** qwen3.6:35b / custom

### 3. Frontend Worker Cron (dev-looper-frontend)  
- **Schedule:** every 1m
- **Model:** qwen3.6:35b / custom

## Configurations

- `REPO=/home/kali/projects/reddit-crawler`
- `BE_DIR=$REPO/backend-java`
- `FE_DIR=$REPO/frontend`
- `OLLAMA_ENDPOINT=http://192.168.100.1:11434/v1`

## Workflow par Tick

### PM Orchestrator (every 30s)
1. `cat BACKLOG.md | grep "\- \[ \]"` → liste des tâches pending
2. `delegate_task(goal="Implement task X", toolsets=["terminal","file"])` → assigner au worker approprié
3. Vérifier build: `./mvnw clean package -DskipTests && npm run build`
4. Docker: `docker compose up --profile all -d`
5. Health check: `curl http://localhost:8080/api/stats | jq '.totalX'`
6. Rapporter dans le format Tick Report ci-dessous

### Worker Backend (every 1m)
1. Ouvrir une session OpenCode interactive: `opencode run "Implement feature Y from BACKLOG"` via terminal background
2. Compiler: `./mvnw clean package -DskipTests`
3. Si BUILD SUCCESS → commit + push
4. Rapporter résultat

### Worker Frontend (every 1m)
1. Ouvrir session OpenCode: `opencode run "Develop component Z from BACKLOG"` via terminal background  
2. Build: `npm run build`
3. Fixer les TSX errors en batch
4. Si BUILD SUCCESS → commit + push

### Worker QA (every 2m)
1. Lancer tests: `./mvnw verify` et `npm test`  
2. Rapporter coverage delta
3. Si échec → delegate_task à dev worker pour fix

## Format de Rapport (Tick Report)

```markdown
### Dev Factory Tick #N | HH:MM
**PM:** Backlog [taskX] assigned to [backend/frontend worker]
**Backend:** [BUILD status] - committed files X
**Frontend:** [BUILD status] - committed files Y  
**Docker:** [status services running / down name]
**Health:** API [HEALTHY/UNREACHABLE on port 8080]
**GPU:** Active (model: qwen3.6:35b via Ollama)

▶️ **Next Tasks:**
1. TaskA (PRIORITY H)
2. TaskB (PRIORITY M)
```

## Skills Requis pour le Workflow

- `pm-orchestrator` → orchestrer PRs, merges, builds, deploy
- `deploy-pipeline-weekly` → pipeline Docker + GitHub workflow
- `unified-po-dev-cron` → cycle PO dev autonome complet
- `dev-factory-orchestrator-patterns` → patterns de détection anti-traps

## Cron Jobs Configuration

### admin-dev-factory (PM Orchestrator)
```yaml
job_id: admin-dev-factory
name: PM Orchestrator (Dev Factory Core)
schedule: every 30s
skill: pm-orchestrator
model: qwen3.6:35b (custom provider → Ollama distant)  
prompt: "Tu es le Product Manager pour reddit-crawler. Chaque tick: (1) lis BACKLOG.md, (2) assigne la task prioritaire via delegate_task au dev worker approprié, (3) verify builds, (4) docker compose up, (5) health check API + Frontend, (6) rapport format Tick Report ci-dessus."
```

### dev-backend-worker
```yaml  
job_id: dev-backend-worker
name: Backend Dev Worker  
schedule: every 1m
skill: opencode
model: qwen3.6:35b
prompt: "Tu es le Developer Backend pour reddit-crawler. Chaque tick: (1) lit BACKLOG.md, (2) implemente la task backend assignée avec OpenCode, (3) compile ./mvnw clean package, (4) commit + push, (5) rapport."
```

### dev-frontend-worker
```yaml
job_id: dev-frontend-worker  
name: Frontend Dev Worker
schedule: every 1m
skill: opencode
model: qwen3.6:35b
prompt: "Tu es le Developer Frontend pour reddit-crawler. Chaque tick: (1) lit BACKLOG.md, (2) implemente la task frontend assignée avec OpenCode, (3) compile npm run build, (4) commit + push, (5) rapport."
```

## Anti-Patterns Evités (d'après dev-factory-orchestrator-patterns)

- ❌ Cron espacés > 5min → GPU gaps visibles
- ❌ PM qui decide mais ne delegue pas aux workers
- ❌ Workers qui attendent le PM au lieu de bosser en continu  
- ❌ Builds Docker avec profiles non specifiés (→ toujours `--profile all`)
- ❌ Git dirty state entre ticks → nettoyer AVANT chaque commit

## Verification Post-Setup

1. `cronjob(action='list')` → vérifier 3 jobs actifs et scheduled
2. Attendre 1 tick (~30s) → vérifier rapport dans channel Discord  
3. Vérifier GPU activity via `nvidia-smi` (doit montrer usage continu)
4. Vérifier app accessible: `curl http://localhost:80/ && echo 'OK'`
