# Docker Compose Unification - April 5, 2026

## Overview

Consolidated duplicate Docker Compose configuration files into a single, robust `docker-compose.yml` file with production-ready features.

## Changes Made

### ✅ Removed
- `docker-compose.yaml` (old legacy file)
- Duplicated service configurations

### ✅ Created
1. **docker-compose.yml** - Unified, production-ready configuration
2. **nginx.conf** - Reverse proxy configuration for production deployments

## New Docker Compose Features

### Services

1. **PostgreSQL 15**
   - Alpine image for minimal size
   - Health checks with 10s intervals
   - Auto-initialization from Flyway migrations
   - Persistent data volume
   - JSON logging with rotation
   - Resource isolation via network

2. **Spring Boot API**
   - Multi-stage Docker build
   - Java 21 with Spring Boot 3.2
   - Environment-based profiles (dev/prod)
   - Resource limits (2 CPU, 2GB memory)
   - Health check dependency
   - Flyway migrations mounted for development

3. **Next.js Frontend**
   - Multi-stage build
   - API proxy configuration
   - Development-friendly setup
   - Resource isolation

4. **Nginx Reverse Proxy** (optional)
   - Port 80 for production deployments
   - Automatic routing to frontend and API
   - Health check endpoints
   - API documentation routing
   - Static file caching

### Advanced Features

#### Docker Compose Profiles
- `all` - Full stack deployment
- `backend` - API only (PostgreSQL + Spring Boot)
- `frontend` - Frontend only (for development)
- `proxy` - Nginx reverse proxy

#### Health Checks
- PostgreSQL: `pg_isready` health probe
- API: Spring Actuator health endpoint
- Smart dependency ordering with `condition: service_healthy`

#### Logging
- JSON file driver for all services
- 10MB max file size
- 3 file rotation limit
- Reduced disk footprint

#### Resource Limits
- API: 2 CPU cores, 2GB memory max
- 0.5 CPU cores, 512MB reserved baseline
- Prevents resource contention

#### Network Isolation
- Custom bridge network: `reddit-network`
- Subnet: `172.28.0.0/16`
- Service communication via container names
- External network isolation

### Environment Variables

```bash
# PostgreSQL
POSTGRES_PASSWORD=your_secure_password

# Spring Profile
SPRING_PROFILES_ACTIVE=dev|prod

# Build Environment
BUILD_ENV=dev|prod

# Optional Settings
SPRING_DOC_ENABLED=true|false
```

## Usage Examples

### Development (Full Stack)
```bash
# Start all services
docker compose --profile all up -d

# View logs
docker compose logs -f

# Stop all services
docker compose --profile all down
```

### Backend Only
```bash
docker compose --profile backend up -d
```

### Frontend Development
```bash
docker compose --profile frontend up -d
```

### Production Deployment
```bash
# Set production environment
export SPRING_PROFILES_ACTIVE=prod
export BUILD_ENV=prod

# Deploy full stack with nginx
docker compose --profile all up -d

# Access via port 80
http://your-server/
```

### Development Backend Only
```bash
# Quick backend iteration
docker compose --profile backend up
```

## File Structure

```
integrated-reddit-crawler/
├── docker-compose.yml       # Unified orchestration
├── nginx.conf               # Reverse proxy config
├── backend-java/
│   ├── src/main/resources/db/migration/
│   │   ├── V1__init_extensions.sql
│   │   ├── V2__scraping_sessions.sql
│   │   ├── V3__posts.sql
│   │   └── V4__comments.sql
│   └── Dockerfile
├── frontend/
│   └── Dockerfile
├── .env
└── .env.example
```

## Benefits

1. **Single Source of Truth** - No duplicate configurations
2. **Production Ready** - Health checks, logging, resource limits
3. **Flexible Deployment** - Profiles for different use cases
4. **Scalable** - Easy to add more services
5. **Secure** - Network isolation, non-root containers
6. **Maintainable** - Clear structure, documented configuration
7. **Dev/Prod Parity** - Same compose file, different profiles

## Migration from Old Setup

| Before | After |
|--------|-------|
| `docker-compose.yaml` | `docker-compose.yml` (standard) |
| `docker-compose.yml` (legacy) | Removed |
| No nginx proxy | Nginx reverse proxy |
| Basic health checks | Enhanced health checks |
| No resource limits | CPU/memory constraints |
| Simple logging | JSON logging with rotation |
| Single deployment mode | Profile-based deployment |

## Next Steps

1. **CI/CD Integration** - Add GitHub Actions for automated builds
2. **Monitoring** - Integrate Prometheus + Grafana
3. **Security** - Enable SSL/TLS for production
4. **Backups** - Automated PostgreSQL backups
5. **Scaling** - Multi-instance deployment configuration

---

**Created**: April 5, 2026  
**Updated By**: Software Manager Team  
**Status**: ✅ Production-Ready
