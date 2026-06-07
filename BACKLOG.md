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

### P3 — Polish (next cycle)
- [ ] **#P3-1**: Backend integration test suite (WebMvcTest + MockRestServiceServer)
- [ ] **#P3-2**: Frontend error boundaries + loading skeletons for all panels
- [ ] **#P4-1**: Multi-config Reddit API key rotation in task queue
- [ ] **#P4-2**: Full integration: frontend → real backend data (remove all mock/stub)
- [ ] **#P4-3**: Flyway migration scripts replacing ddl-auto=update for production
