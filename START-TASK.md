# START-TASK.md - Reddit Crawler Development

**Session**: 2026-04-04 Night Session
**Duration**: 7+ hours overnight
**Focus**: Complete Phase 1 Foundation + start Phase 2

---

## Project Context

You are part of the ArabTooling development team working on the **Reddit Crawler** project.

**GitHub**: https://github.com/ArabTooling/reddit-crawler
**Tech Stack**: 
- Backend: FastAPI (Python 3.11+), SQLAlchemy, PRAW
- Frontend: Next.js 14+, TailwindCSS, shadcn/ui (slate theme)
- Database: PostgreSQL
- DevOps: Docker, GitHub Actions

---

## Your Role

### Backend Developer (backend-dev agent)
- Build FastAPI backend with Reddit scraping
- Implement REST API endpoints
- Database modeling and migrations
- Write unit and integration tests

### Fullstack Developer (fullstack-dev agent)  
- Build Next.js frontend dashboard
- Implement real-time updates
- Data visualization and export features
- Write component and E2E tests

---

## Current Status (as of 2026-04-04 19:00)

### Backend:
✅ Project structure created
✅ Database models defined (Post, Comment, ScrapingSession)
✅ PRAW scraper integration started
✅ API endpoints skeleton created
✅ Async crawler service started
✅ Health check endpoint implemented

❌ **Pending**: Unit tests for scrapers and API
❌ **Pending**: Install dependencies and verify

### Frontend:
✅ Next.js project initialized
✅ TailwindCSS + shadcn/ui configured
✅ Dashboard page created
✅ Controls page created
✅ Data viewer page created
✅ Settings page created
✅ React Query integration complete

❌ **Pending**: Authentication UI
❌ **Pending**: Component tests

---

## Your Task

### Phase 1 Completion (Priority - Complete Tonight)

#### Backend Dev Tasks:
1. **Complete Unit Tests**
   - Test PRAW scraper integration
   - Test API endpoints
   - Test database operations
   - Test async crawler service

2. **Install Dependencies**
   - Install Python dependencies (requirements.txt)
   - Set up PostgreSQL database
   - Run database migrations
   - Verify all components work

3. **Code Quality**
   - Run linting (black, isort, flake8)
   - Run type checking (mypy)
   - Achieve 90%+ test coverage

4. **Documentation**
   - Update README.md with setup instructions
   - Document all API endpoints (OpenAPI/Swagger)
   - Create .env.example with all variables

5. **Commit Work**
   - Commit completed phase 1 work
   - Push to github (origin/main)
   - Create branch tag: phase1-complete

#### Fullstack Dev Tasks:
1. **Complete Authentication UI**
   - Login page
   - Register page
   - JWT/session handling
   - Protected routes

2. **Enhance Dashboard**
   - Real-time stats updates (WebSocket/SSE)
   - Charts for data visualization
   - Export functionality (CSV/JSON)
   - Notification system

3. **Add Error Handling**
   - Loading states
   - Error boundaries
   - User-friendly error messages
   - Retry logic

4. **Write Tests**
   - Component unit tests (Vitest/React Testing Library)
   - E2E tests (Playwright)
   - Integration tests

5. **Code Quality**
   - Run linter (ESLint, Prettier)
   - Type checking (TypeScript strict mode)
   - Performance optimization

6. **Commit Work**
   - Commit completed phase 1 work
   - Push to github (origin/main)
   - Create branch tag: phase1-complete

---

## Quality Standards

### PACT Protocol (Contract-First Development)

Before implementing any feature:

1. **Problem**: Restate objective and current state
2. **Acceptance**: Define 3-5 specific checks
3. **Change**: Produce minimal, focused diff
4. **Trace**: Show evidence of success

**Example Contract:**
```
Objective: Complete backend unit tests for PRAW scraper
Acceptance:
  - All scraper methods have test coverage
  - Mock Reddit API calls in tests
  - Test edge cases (rate limits, errors)
  - Coverage >= 90%
  - All tests pass
Non-goals: Integration tests, live Reddit scraping
Constraints: Use pytest, async/await, mock libraries
```

---

## Working Workflow

### 1. Start Each Session
- Pull latest from main branch
- Check git status
- Review recent commits
- Read ROADMAP.md for priorities
- Check HEARTBEAT.md for scheduled checks

### 2. Before Coding
- Define PACT contract
- Identify which files to modify/create
- Check dependencies
- Verify test environment

### 3. During Development
- Make small commits
- Run tests frequently
- Update documentation
- Log progress in memory/

### 4. End of Session
- Commit all changes
- Push to remote
- Document what was completed
- Note what's next
- Update memory file with lessons learned

---

## Important Notes

### GitHub Integration
- All work must be committed and pushed
- Use feature branches for major work
- Create PRs for team review
- Tag releases appropriately

### Docker
- Keep images optimized (multi-stage builds)
- Use .dockerignore to exclude unnecessary files
- Test locally before pushing

### Communication
- Log progress in memory/YYYY-MM-DD.md
- Update HEARTBEAT.md if issues arise
- Notify team in Discord for blockers

---

## Tonight's Goals

**By 02:00 (3 hours)**: Phase 1 backend and frontend COMPLETE
**By 05:00 (6 hours)**: Phase 2 core features started
**By 06:00 (7 hours)**: Submit PRs for review

---

## Success Metrics

- ✅ All Phase 1 tests pass
- ✅ 90%+ code coverage
- ✅ No linting errors
- ✅ All changes pushed to GitHub
- ✅ Database migrations complete
- ✅ Docker images build successfully
- ✅ API documentation generated
- ✅ Frontend responsive on all devices

---

**Remember**: You're the specialist. Make the code production-ready, write comprehensive tests, and document everything clearly. The software-manager will review and coordinate with you.

**Good luck! Let's ship something amazing!** 🚀
