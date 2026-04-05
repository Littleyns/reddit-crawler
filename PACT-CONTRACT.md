# PACT Contract - Phase 1 Reddit Crawler Foundation

## **P**roblem

Build a production-ready Reddit crawler system with FastAPI backend and Next.js frontend that can asynchronously scrape Reddit posts and comments, store them in PostgreSQL, and provide a real-time dashboard for monitoring and data export.

## **A**cceptance Criteria

**Backend:**
1. FastAPI application with proper configuration management (Pydantic settings)
2. PostgreSQL database models: Post, Comment, ScrapingSession with proper relationships
3. PRAW scraper implementation with async operations
4. REST API endpoints: POST /api/crawler/start, GET /api/crawler/status, GET /api/data/posts, GET /api/data/comments
5. Async crawler service with threading support
6. Unit tests with 90%+ code coverage
7. All linting checks pass (black, isort, flake8, mypy)
8. Requirements.txt and .env.example created

**Frontend:**
1. Next.js 14+ app with TypeScript and TailwindCSS
2. Authentication UI (login/register pages)
3. Dashboard with real-time stats and crawler status
4. Data viewer with pagination and search
5. API client with proper error handling
6. Responsive design that works on mobile and desktop
7. All TypeScript checks pass
8. ESLint/Prettier clean

**Git:**
1. All code committed with meaningful messages
2. Pushed to GitHub organization
3. Tagged phase1-complete

## **N**on-goals
- Real-time WebSocket streaming (can use polling for now)
- User preference system
- Advanced filtering/search beyond basic pagination
- Production deployment (focus on development setup)

## **T**race and Test

**Before:** Empty workspace, no code
**After:** Complete Phase 1 foundation with all tests passing

**Tests to run:**
```bash
# Backend
cd backend
pytest tests/ -v --cov=src --cov-report=term-missing
black --check src/ tests/
flake8 src/ tests/
mypy src/

# Frontend
cd frontend
npm run lint
npm run type-check
npm test
```

## Constraints
- Stack: FastAPI (Python 3.12+), Next.js 14+, PostgreSQL 15+, PRAW
- Time: 7+ hours for complete Phase 1
- Quality: Production-ready code, no technical debt
- Git: Commit every 2 hours minimum

---

**Signed:** Software Manager & Development Team
**Date:** 2026-04-04
**Target:** Phase 1 Complete