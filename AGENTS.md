# Reddit Crawler - Agent Development Protocol

## Project Identity
- **GitHub User:** Littleyns (formerly ArabTooling)
- **Repo URL:** https://github.com/Littleyns/reddit-crawler
- **Default Branch:** main

## Team
- Organization: Littleyns (was ArabTooling)
- Project Owner: Littleyouness (@Littleyounes) — Discord ID: `306798001886593026`
- DevOps: Slim-Shady — Discord ID: `1488983572428751010`
- Data Science: Zarrouk6969 — Discord ID: `1488985589574537347`
- Development Lead: Hermes Agent (automated)

## Current Status
- **Phase:** Phase 2 - Core Features Integration
- **Last Milestone:** Spring Boot migration merged (PR #12)
- **Critical Gap:** Frontend API routes call mock data, not real backend

## Architecture Summary
```
┌─────────────┐     ┌──────────────┐     ┌──────────┐
│  Next.js 16 │────▶│ Spring Boot  │────▶│ PostgreSQL│
│  Frontend   │◀────│ Backend      │     │          │
│  port:3000  │     │ port:8080    │     │ port:5432│
└─────────────┘     └──────────────┘     └──────────┘
```

## Branch Naming Convention
- Feature branches: `<agent/<scope>/<feature-name>` or `feature/<name>`
- Hotfixes: `hotfix/<issue-desc>`
- Merge PRs to: `main` (not develop) from Phase 3 onward

## Git Workflow (MANDATORY for all agents)
1. Always run `git pull --rebase origin main` before starting work
2. Create branch: `git checkout -b <name> main`
3. Write tests FIRST (TDD), pass them, then write implementation
4. Run full test suite before commit: `mvn verify && npm test`
5. Commit messages follow Conventional Commits: `type(scope): message`
6. NEVER push directly to main — open PR, request review from @Littleyounes
7. Type: `feat/fix/docs/test/chore/refactor/perf/security/ci/build`

## Deployment (Coolify)
- **Instance:** http://162.19.205.8:8000 (self-hosted Coolify)
- **CLI version:** 1.6.2+ located at `~/.local/bin/coolify`
- **Context name:** `myvm` (stores server URL + API token)
- **Deploy command:** `cd <repo-dir> && ~/.local/bin/coolify app deploy <app-name>`
- Load `coolify-cli` skill for detailed commands before deploying/managing resources

## Required Skills for Subagents
All subagent workers MUST load these skills:
1. Load `writing-plans` → write plans with bite-sized tasks (2-5 min each)
2. Plan includes exact file paths, complete code examples, test commands with expected output
3. Follow TDD cycle per task: write failing test → run (verify fail) → minimal impl → run (verify pass)
4. Run full test suite after each commit to catch regressions
5. Submit changes via PR, never direct push

## Code Review Rules
- All review subagents must check: security (SQLi/XSS/CSRF), test coverage %, lint compliance, API contract matching
- SPEC gaps are CRITICAL — implementer must fix spec-compliance failures before quality review
- NEVER skip the two-stage review process (spec compliance → code quality)

## Quality Gates Before PR Merge
- [ ] All tests pass (`mvn verify` and `npm test`)
- [ ] 0 lint errors in backend and frontend
- [ ] API contract matches PACT-CONTRACT.md requirements
- [ ] Docker compose brings all services up without error
- [ ] No secrets / credentials / tokens committed to repo (check diffs!)
- [ ] PR description includes evidence (test output URLs, screenshots if UI change)
