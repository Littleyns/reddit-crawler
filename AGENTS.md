# Reddit Crawler - Agent Development Protocol (v2 — OpenCode + Ollama Local)

## Project Identity
- **GitHub User:** Littleyns (formerly ArabTooling)
- **Repo URL:** https://github.com/Littleyns/reddit-crawler
- **Default Branch:** main

## Team
- Organization: Littleyns (was ArabTooling)
- Project Owner: Littleyouness (@Littleyounes) — Discord ID: `306798001886593026`
- DevOps: Slim-Shady — Discord ID: `1488983572428751010`
- Data Science: Zarrouk6969 — Discord ID: `1488985589574537347`

## Model & Coding Agent Stack
- **Model:** `qwen3.6:35b` (Qwen MoE 35B, GGUF Q4_K_M, 22GB)
- **Provider:** Ollama local via HTTP API
- **Endpoint:** `http://192.168.100.1:11434/v1`
- **Coding Agent:** OpenCode CLI (`opencode run '...'`) — provider: openai, baseUrl: ollama endpoint
- **Model in OpenCode:** `qwen3.6:35b` with OpenAI compatibility layer

### OpenCode Configuration (ALL workers MUST use this)
```json
{
  "$schema": "https://opencode.ai/config.json",
  "provider": "openai",
  "openai": {
    "baseUrl": "http://192.168.100.1:11434/v1"
  }
}
```

### OpenCode Usage for ALL Dev Workers
- One-shot tasks: `opencode run 'prompt'` (no pty needed) — use `--model qwen3.6:35b` if not auto-detected
- Context-aware: Always provide full file paths and code snippets
- Parallel safe: Each worker uses its own workdir/worktree to avoid collisions
- Verify output: Test compilation (`./mvnw clean package -DskipTests`) or frontend build (`npm run build`) BEFORE committing

**CRITICAL RULES for OpenCode workers:**
1. Never use external models (GPT, Claude, etc.) — always `qwen3.6:35b` via localhost Ollama
2. No npm global packages that require OAuth login — all work done locally
3. Output must be verifiable (test compile/test build runs) not just written code
4. When writing code, include the full file content (not diffs) since OpenCode writes directly

## Current Status & Active Bugs (MUST FIX FIRST)
### 🔴 CRITICAL: Docker/Coolify Deployment Broken (since June 2026)
- `docker-compose.yml` defines all services with `profiles: [all, backend]` but no containers running
- Backend API (`reddit-api`) fails to deploy on Coolify — **unknown root cause since June**
- Priority: diagnose and fix Docker build + deploy pipeline before any feature work

### 🟡 Frontend Stagnation (2+ days without progress)
- `analytics/page.tsx` references undefined hooks: `useHeatmap()`, `useKeywords()`
- Missing: StatCard component, ChartSkeleton component
- Analytics page fails to compile (`react-query` import but not needed + missing type imports: `useMemo`)

### 🟢 Backend API (Partial)
- Spring Boot 3.2 runs on port 8080 ✅ (when containers are up)
- Hibernate fix applied: `LOWER()` JPQL queries → native SQL with ILIKE PostgreSQL syntax
- Config test endpoint exists: `/api/config/test` (GET — returns credential validation status)

### 📋 Feature Queue (after stack is stable)
1. Round-robin multi-config support for Reddit API keys in task queue
2. Complete integration: backend API endpoints → frontend Next.js data fetches
3. Analytics pages fully functional with real data (no more mock/stub data)

## Architecture Summary
```
┌─────────────┐     ┌──────────────┐     ┌──────────┐
│  Next.js 16 │────▶│ Spring Boot  │────▶│ PostgreSQL│
│  Frontend   │◀────│ Backend      │     │          │
│  port:3000  │     │ port:8080    │     │ port:5432│
└─────────────┘     └──────────────┘     └──────────┘
                        ↑
                   OpenCode workers use this model for ALL code
```

## Branch Naming Convention
- Feature branches: `feature/<name>` or `<agent>/<scope>/<feature-name>`
- Hotfixes: `hotfix/<issue-desc>`
- Merge PRs to: `main` (not develop) from Phase 3 onward

## Git Workflow (MANDATORY for all agents)
1. Always run `git pull --rebase origin main` before starting work
2. Create branch: `git checkout -b <name> main`
3. Write tests FIRST (TDD), pass them, then write implementation
4. Run full test suite before commit: `mvn verify && npm test`
5. Commit messages follow Conventional Commits: `type(scope): message`
6. NEVER push directly to main — open PR, request review from @Littleyounes

## Deployment (Coolify)
- **Instance:** http://162.19.205.8:8000 (self-hosted Coolify)
- **CLI version:** 1.6.2+ located at `~/.local/bin/coolify`
- **Deploy command:** `cd <repo-dir> && ~/.local/bin/coolify app deploy <app-name>`

## Coding Agent Protocol (OpenCode Workers)
### Worker Assignment Pattern
Each worker handles a SINGLE domain per dev cycle:
1. **Backend Worker** — Spring Boot Java backend, Dockerfile, API endpoints
2. **Frontend Worker** — Next.js frontend pages, components, hooks
3. **Integration Worker** — Connects frontend to real backend data (no mock)

### OpenCode Commands
```bash
# Backend work (Java/Spring Boot):
opencode run 'Refactor PostRepository to use native SQL' -f /home/kali/projects/reddit-crawler/draw the line in AGENTS.md and make it clear which agent handles what domain. Don't mix backend work with frontend work in the same worker session, ever." --model qwen3.6:35b

# Frontend work (Next.js/React):
opencode run 'Fix analytics/page.tsx missing hooks' -f /home/kali/projects/reddit-crawler/draw the line in AGENTS.md and make it clear which agent handles what domain. Don't mix backend work with frontend work in the same worker session, ever." --model qwen3.6:35b

# Fix Docker/Coolify issues (infrastructure):
opencode run 'Fix docker-compose.yml profiles and Spring config for Coolify deployment' -f /home/kali/projects/reddit-crawler/draw the line in AGENTS.md and make it clear which agent handles what domain. Don't mix backend work with frontend work in the same worker session, ever." --model qwen3.6:35b

### Verification Protocol
After writing code, workers MUST verify:
- `./mvnw clean package -DskipTests` → BUILD SUCCESS (backend)
- `npm run build` → 0 errors (frontend Next.js)  
- No secrets/tokens in diffs (`git diff --name-only` checked against `.env*`)

## Code Review Rules
- All review subagents must check: security (SQLi/XSS/CSRF), test coverage %, lint compliance, API contract matching
- SPEC gaps are CRITICAL — implementer must fix spec-compliance failures before quality review
- NEVER skip the two-stage review process (spec compliance → code quality)

## Quality Gates Before PR Merge
- [ ] All tests pass (`mvn verify` and `npm test`)
- [ ] 0 lint errors in backend and frontend
- [ ] Docker compose brings all services up without error
- [ ] No secrets / credentials / tokens committed to repo (check diffs!)
- [ ] PR description includes evidence (test output URLs, screenshots if UI change)

## Profile Configurations for OpenCode Workers
Each profile runs in isolated config directory:

### Backend Worker Profile (`~/.hermes/profiles/backend-worker/`)
```json
{ "$schema": "https://opencode.ai/config.json", "provider": "openai" }
```
- Workdir: `/home/kali/projects/reddit-crawler/backend-java/`
- Always checks `./mvnw clean package -DskipTests` before finish

### Frontend Worker Profile (`~/.hermes/profiles/frontend-worker/`)
```json
{ "$schema": "https://opencode.ai/config.json", "provider": "openai" }
```
- Workdir: `/home/kali/projects/reddit-crawler/frontend/`
- Always checks `npm run build` before finish

## Spring Boot Hibernate Fix Applied (June 2026)
- **Problem:** Hibernate 6.3 incompatible with JPQL `LOWER()` on TEXT columns in PostgreSQL
- **Solution:** Use native queries with PostgreSQL `ILIKE` instead of `LOWER(p.body)` pattern matching
- Files fixed: `PostRepository.java` — all `@Query(..., nativeQuery = true)` where LOWER was used

## Database Strategy (Temporary)
- Hibernate JPA ddl-auto set to `create-drop` in Spring config during dev
- Data lost on restart but prevents migration conflicts during development phase
- Production will use Flyway migrations when stable
