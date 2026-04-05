# Reddit Crawler

A production-ready Reddit crawling and analytics platform built with **Java Spring Boot 3.x**, **PostgreSQL**, and a modern **Next.js 16** frontend.

## Overview

- **Frontend**: Next.js 16 App Router dashboard for crawl control, telemetry, data browsing, exports, settings, and authentication
- **Backend**: Java Spring Boot 3.2 REST API with JWT authentication, Spring Security, and full CRUD operations
- **Database**: PostgreSQL 15 with Flyway versioned migrations
- **Deployment**: Docker Compose with multi-stage builds, health checks, and production-ready configuration

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Frontend** | Next.js 16, React 19, TypeScript, TanStack Query, React Hook Form, Zod |
| **Backend** | Java 21, Spring Boot 3.2, Spring Security, JWT |
| **Database** | PostgreSQL 15, Flyway migrations, Spring Data JPA |
| **ORM** | Hibernate, MapStruct |
| **Security** | JWT tokens, BCrypt, Role-based access control |
| **DevOps** | Docker Compose, Multi-stage builds, Maven |
| **Docs** | Springdoc OpenAPI, Swagger UI |

## Quick Start

```bash
# Clone and setup
git clone https://github.com/ArabTooling/reddit-crawler.git
cd reddit-crawler/integrated-reddit-crawler

# Copy environment files
cp .env.example .env

# Build and run
docker compose up --build -d
```

**Access Points:**
- Frontend: `http://localhost:3000`
- API: `http://localhost:8080`
- API Docs: `http://localhost:8080/swagger-ui.html`

## Features

### Crawler Control
- Start/stop crawl sessions
- Real-time status monitoring
- Subreddit-specific crawling
- Progress tracking

### Data Management
- Browse scraped posts and comments
- Full-text search
- Pagination support
- Export functionality

### Security
- JWT authentication
- Role-based access (ADMIN, OPERATOR, VIEWER)
- Protected API endpoints
- Secure password hashing

## API Endpoints

See **Springdoc OpenAPI** at `http://localhost:8080/swagger-ui.html` for complete API documentation.

**Key Endpoints:**
- `POST /api/crawler/start` - Start crawl
- `GET /api/data/posts` - List posts
- `GET /api/data/comments` - List comments
- `POST /api/auth/login` - Login

## Development

See full documentation in the project README for detailed setup, environment configuration, deployment instructions, and development guides.

---

**Built with ❤️ by the ArabTooling team**
