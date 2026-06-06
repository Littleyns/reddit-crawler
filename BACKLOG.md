Reddit Crawler — Autonomous Dev Factory Backlog

## Architecture
- Backend: Spring Boot 3.2 / Java 21 (SpringDoc OpenAPI, Mapstruct, Lombok)
- Frontend: Next.js 16 (App Router), Tailwind CSS, recharts
- Infra: Docker Compose, Postgres:15, Redis:7, Nginx reverse proxy
- LLM: OpenCode CLI with local Ollama endpoint @ 192.168.100.1:11434

## Tasks

### P0 — Critical Bugs (fix first)
- [x] [DONE] ] **#P0-1**: Backend container keeps crashing on startup despite BUILD SUCCESS
  - Symptoms: docker compose ps shows "Up but unhealthy" for reddit-api
  - Root cause likely: JPA/Hibernate initialization failure, bean injection error, or security filter chain deadlock
  
### P1 — Core Features (highest business value)  
- [ ] **#P1-1**: CrawlerJob REST API endpoints (POST /start, GET /{id}, POST /stop)
- [ ] **#P1-2**: Async crawler runner (@Scheduled + Reddit JSON API client)
- [ ] **#P1-3**: Post/Comment ingestion into PostgreSQL (PostRepository + CrawlJobRepository)
- [ ] **#P1-4**: Redis cache layer for crawl results with TTL 30min configurable
- [ ] **#P1-5**: LLM-powered sentiment analysis pipeline (via Ollama remote)

### P2 — Frontend Enhancements
- [ ] **#P2-1**: Crawler management page (/controls) - create jobs, view status, start/stop  
- [ ] **#P2-2**: Real-time dashboard metrics with live API polling every 30s
- [ ] **#P2-3**: Analytics deep-dive page (/analytics) - keyword distribution + trends

### P3 — Polish (nice to have)
- [ ] **#P3-1**: Backend integration test suite (WebMvcTest + MockRestServiceServer)
- [ ] **#P3-2**: Frontend error boundaries + loading skeletons for all panels
