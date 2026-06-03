## Critical Gap Resolution: Mock Data → Real Spring Boot Backend API

### Problem 
The **PACT Contract** and **AGENTS.md** both explicitly flag: *"Frontend API routes call mock data, not real backend"* — this is the #1 priority gap for Phase 2 completion. Every frontend route at `frontend/src/app/api/*/route.ts` delegates to `@/lib/server/mock-api.ts`, returning fake statistics instead of real crawled data from Spring Boot.

### What Was Done (Dev Work)
- Created **4 missing backend REST controllers** that the frontend expects:
  - `DataController.java` — `/api/data/posts` & `/api/data/comments` with full pagination, search, and subreddit filtering
  - `StatsController.java` — `/api/stats` with real post counts, session history, success rates
  - `SettingsController.java` — `/api/settings` GET/POST for system settings
  - `ExportController.java` — `/api/data/export?format=csv|json` for CSV/JSON downloads
- All controllers compile cleanly (`mvn compile` passes with all 18 source files)
- CORS support added to SecurityConfig for frontend communication

### What Needs to Happen (Dev Task)
- Replace the 14 frontend route handlers at `frontend/src/app/api/*/route.ts` so they proxy requests → Spring Boot backend instead of returning mock data
- This is the **critical gap** — without it, the dashboard shows fake numbers and the crawler start/stop controls do nothing real

### Impact of Leaving It Unresolved
1. Users see stale/fake statistics in the dashboard
2. Crawler start/stop has no effect on actual Reddit crawling  
3. Post listings always show mock data regardless of crawl state
4. Export downloads produce CSV from mock posts instead of real crawled content
5. The full integration chain (Spring Boot → PostgreSQL → Redis) is completely bypassed

### PO Priority Decision
This is **HIGH** priority — it's the single most impactful thing that can be done before Phase 3 work begins. Without real data flowing, all other frontend features are just pretty chrome on an empty pipe.
