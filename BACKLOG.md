Reddit Crawler — Autonomous Dev Factory Backlog

## Architecture
- Backend: Spring Boot 3.2 / Java 21 (SpringDoc OpenAPI, Mapstruct, Lombok)
- Frontend: Next.js 16 (App Router), Tailwind CSS, recharts
- Infra: Docker Compose, Postgres:15, Redis:7, Nginx reverse proxy
- LLM: OpenCode CLI with local Ollama endpoint @ 192.168.100.1:11434

## Tasks

### P0 — Critical Bugs (fix first) ✅ COMPLETE
- [x] **#P0-1**: Backend container keeps crashing on startup despite BUILD SUCCESS
  - FIXED: Security filter chain + Hibernate dialect fixed; containers stay healthy

### P1 — Core Features ✅ ALL COMPLETE
- [x] **#P1-1**: CrawlerJob REST API endpoints (POST /start, GET /{id}, POST /stop)
  - DONE: CrawlerController.java (5 endpoints: /start, /status/{id}, /status, /stop, /jobs)
- [x] **#P1-2**: Async crawler runner (@Scheduled + Reddit JSON API client)
  - DONE: AsyncCrawlerRunner.java with @Async crawlExecutor pool + @Scheduled health sweeps
- [x] **#P1-3**: Post/Comment ingestion into PostgreSQL (PostRepository + CrawlJobRepository)
  - DONE: All 4 entity repositories implemented; post/comment persistence wired in service layer
- [x] **#P1-4**: Redis cache layer for crawl results with TTL configurable
  - DONE: RedisCache.java with RedisTemplate, configurable TTL via properties
- [x] **#P1-5**: LLM-powered sentiment analysis pipeline (via Ollama remote)
  - DONE: LlmSentimentAnalysisService + LlmSentimentAnalysisController; /api/llm/sentiment responds OK

### P2 — Frontend Enhancements ✅ ALL COMPLETE
- [x] **#P2-1**: Crawler management page (/controls) - create jobs, view status, start/stop
  - DONE: Full ControlsPage with telemetry sidebar + StatusBadge
- [x] **#P2-2**: Real-time dashboard metrics with live API polling every 30s
  - DONE: DashboardPage with useCrawlerStatus (15s refetch) + useStats + mock fallback
- [x] **#P2-3**: Analytics deep-dive page (/analytics) - keyword distribution + trends
  - DONE: Full analytics page with recharts LineChart, BarChart, PieChart; useAnalytics hook polling backend

### P3 — Polish ✅ ALL COMPLETE
- [x] **#P3-1**: Backend integration test suite (WebMvcTest + MockRestServiceServer)
  - FIXED: Wrote CrawlerIntegrationTest, AnalyticsIntegrationTest, HealthIntegrationTest with WebMvcTest slices
- [x] **#P3-2**: Frontend error boundaries + loading skeletons for all panels
  - FIXED: Wrapped all pages in PageErrorBoundary, added PanelSkeleton/GridSkeleton/TableSkeleton loading states

### P4 — Rotation + Production Readiness ✅ ALL COMPLETE (this tick)
- [x] **#P4-1**: Multi-config Reddit API key rotation in task queue
  - DONE: RedditApiRotationService.java (round-robin + auto token refresh), ApiKeysManagementController.java (CRUD), integrated into AsyncCrawlerRunner health-sweep and RedditCrawlerService.startCrawl
- [x] **#P4-2**: Full integration: frontend → real backend data (remove all mock/stub)
  - DONE: All useAnalytics, useStats, useJobs hooks fetch REAL endpoint; no generateMock* functions remain in production code; verified build = 0 TS errors
- [x] **#P4-3**: Flyway migration scripts replacing ddl-auto=update for production
  - DONE: V1–V11 migrations deployed; spring.jpa.hibernate.ddl-auto=none; flyway.enabled=true; migration table auto-baselined

### P5 — Upcoming (next tick)
- [ ] **#P5-1**: Rate-limit aware scheduler (delay between subreddit crawls to avoid 429s)
- [ ] **#P5-2**: Export data page (/data) with CSV/JSON download and pagination UI polish
- [ ] **#P5-3**: Settings page /settings — persist LLM provider, proxy settings, crawler defaults
### P6 — Upcoming (planning)
- [ ] **#P6-1**: Automated test scaffolding — add integration test harness for frontend hooks (useCrawlerStatus, useAnalytics, useStats)
- [ ] **#P6-2**: Logging and monitoring (structured JSON logs, log aggregation config, alerting on failure patterns)

### 🔵 Status Check | 2026-06-07T17:24Z
- **Deploy:** ✅ #110 finished (commit 210dacd — remove nginx mount) — all 5 containers running healthy
- **Kanban:** All previous tasks archived/done → P5-1 rate-limiting created as next priority task

