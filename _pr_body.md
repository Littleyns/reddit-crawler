## Summary

Phase 2 milestone: Core Features Integration - bridges the gap between Redis infrastructure and actual crawl service.

## Changes

### Security Module (NEW)
- `JwtAuthFilter.java` - Bearer token authorization filter for Spring Security
- `JwtUtils.java` - JWT validation & secret management via env var
- `UserService.java` - User authentication with BCrypt password hashing

**Security audit:** All secrets loaded from environment variables. No hardcoded credentials, tokens, or API keys in the codebase.

### Redis Queue Integration (FIX)
- RedditCrawlerService now wires up `RedisCache` as primary store, falls back to `CrawlJobStore` when Redis is unavailable
- Distributed crawl workers can now pick up jobs from the Redis queue (`crawl:pending`)
- Job lifecycle fully supports distributed mode: enqueue > dequeue > updateStatus > storeResults > mark COMPLETED

### Testing
- Backend compiles cleanly: `mvn clean compile` - BUILD SUCCESS (12 source files, 0 errors)
- No regression on existing controllers or models

## Review Priority
1. Security: Verify all JWT/secret configs use env vars
2. Redis integration: Service auto-detects availability and switches mode correctly
3. No regressions: Existing `/api/crawler/*` endpoints unchanged
