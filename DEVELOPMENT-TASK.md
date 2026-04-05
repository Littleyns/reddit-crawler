# Overnight Reddit Crawler Development Task

**Start Time**: 2026-04-04 19:45 UTC  
**Duration**: 7+ hours  
**Goal**: Complete Phase 1 Foundation

## Current Situation

The workspace is empty - we're starting Phase 1 from scratch.

## Development Plan

### Backend (FastAPI + PRAW + PostgreSQL)

**Create structure:**
```
backend/
├── src/
│   ├── __init__.py
│   ├── main.py           # FastAPI app
│   ├── config.py         # Settings
│   ├── database.py       # DB connection
│   ├── models.py         # SQLAlchemy models (Post, Comment, Session)
│   ├── scrapers/
│   │   ├── __init__.py
│   │   ├── praw_scrapers.py    # Main PRAW client
│   │   ├── post_scraper.py     # Extract posts
│   │   └── comment_scraper.py  # Extract comments recursively
│   ├── services/
│   │   ├── __init__.py
│   │   └── crawler_service.py  # Async crawler orchestration
│   └── api/
│       ├── __init__.py
│       ├── endpoints/
│       │   ├── crawler.py      # Crawler control endpoints
│       │   └── data.py         # Data retrieval endpoints
│       └── dependencies.py
├── tests/
│   ├── __init__.py
│   ├── test_scrapers.py    # Unit tests for scrapers
│   └── test_api.py         # Unit tests for API
├── requirements.txt
└── .env.example
```

**Implement:**
1. Database models (Post, Comment, ScrapingSession)
2. PRAW Reddit scraper with async operations
3. REST API endpoints (start, stop, status, data)
4. Async crawler service with threading
5. Unit tests for all components
6. Code quality (black, isort, mypy, flake8)
7. Documentation (README.md, OpenAPI docs)

### Frontend (Next.js 14+ + TailwindCSS + shadcn/ui)

**Create structure:**
```
frontend/
├── src/
│   ├── app/
│   │   ├── layout.tsx
│   │   ├── page.tsx          # Landing
│   │   ├── dashboard/page.tsx
│   │   ├── controls/page.tsx
│   │   ├── data/page.tsx
│   │   └── settings/page.tsx
│   ├── components/
│   │   ├── auth/
│   │   │   ├── LoginForm.tsx
│   │   │   └── RegisterForm.tsx
│   │   ├── dashboard/
│   │   │   ├── StatsCards.tsx
│   │   │   ├── RealTimeChart.tsx
│   │   │   └── CrawlerStatus.tsx
│   │   ├── controls/
│   │   │   ├── CrawlerControl.tsx
│   │   │   └── ExportButton.tsx
│   │   └── ui/               # shadcn/ui components
│   └── lib/
│       ├── api.ts            # API client
│       ├── useAuth.ts        # Auth hook
│       └── useCrawler.ts     # Crawler hook
├── public/
├── tailwind.config.js
├── tsconfig.json
└── next.config.js
```

**Implement:**
1. Authentication UI (login/register with form validation)
2. Dashboard with real-time stats updates
3. Controls page with crawler start/stop and export
4. Data viewer with pagination and search
5. Settings page for API configuration
6. Error handling (loading states, error boundaries, retry logic)
7. Tests (Vitest + Playwright E2E)
8. Code quality (ESLint, Prettier, TypeScript strict)

## Implementation Steps

1. **Read TASK.md and ROADMAP.md** for full context
2. **Create PACT contract** - define objective and acceptance criteria
3. **Implement backend first** - ensure API works
4. **Implement frontend** - connect to backend API
5. **Add testing** - unit tests, E2E tests
6. **Code quality** - linting, type checking, formatting
7. **Documentation** - README, OpenAPI docs
8. **Git operations** - commit, push to GitHub, tag phase1-complete

## Acceptance Criteria (Phase 1)

**Backend:**
- ✅ All scrapers implemented and tested
- ✅ All API endpoints working
- ✅ Unit tests with 90%+ coverage
- ✅ No linting errors
- ✅ Documentation complete

**Frontend:**
- ✅ Authentication UI complete
- ✅ Real-time dashboard working
- ✅ Export functionality functional
- ✅ Error handling comprehensive
- ✅ All tests passing

**Git:**
- ✅ All commits pushed to GitHub
- ✅ Tagged phase1-complete
- ✅ README.md updated with setup instructions

## Start Now!

Begin with:
1. Read TASK.md at /home/kali/.openclaw/workspace/reddit-crawler/TASK.md
2. Read ROADMAP.md at /home/kali/.openclaw/workspace/reddit-crawler/ROADMAP.md
3. Create PACT contract for Phase 1 implementation
4. Implement backend and frontend in parallel or sequentially
5. Monitor progress and report

Good luck! 🚀
