# Reddit Crawler API - Java Spring Boot

## Overview

A production-ready Reddit crawling and analytics platform built with Java Spring Boot 3.x and PostgreSQL.

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.2.0
- **Database**: PostgreSQL with Flyway migrations
- **ORM**: Spring Data JPA with MapStruct
- **Security**: Spring Security with JWT
- **Documentation**: Springdoc OpenAPI (Swagger UI)
- **Build**: Maven

## Project Structure

```
backend-java/
├── src/main/java/com/arabtooling/redditcrawler/
│   ├── config/                    # Configuration classes
│   │   ├── SecurityConfig.java
│   │   ├── WebMvcConfig.java
│   │   └── JwtAuthenticationFilter.java
│   ├── controller/                # REST Controllers
│   │   ├── AuthController.java
│   │   ├── CrawlerController.java
│   │   ├── CommentsController.java
│   │   ├── PostsController.java
│   │   └── SystemController.java
│   ├── dto/                       # Data Transfer Objects
│   ├── entity/                    # JPA Entities
│   │   ├── Comment.java
│   │   ├── Post.java
│   │   ├── ScrapingSession.java
│   │   └── User.java
│   ├── exception/                 # Exception handling
│   │   └── GlobalExceptionHandler.java
│   ├── mapper/                    # MapStruct mappers
│   ├── repository/                # JPA Repositories
│   ├── service/                   # Business logic
│   └── RedditCrawlerApplication.java
├── src/main/resources/
│   ├── db/migration/              # Flyway migrations
│   └── application.yml
└── pom.xml
```

## Quick Start

### Prerequisites

- Java 21 or later
- Maven 3.8+
- PostgreSQL 14+
- Docker (optional, for local development)

### Running Locally

1. **Set up PostgreSQL:**
   ```bash
   docker run --name reddit-postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:15
   ```

2. **Create database:**
   ```bash
   docker exec -it reddit-postgres psql -U postgres -c "CREATE DATABASE reddit_crawler;"
   ```

3. **Run the application:**
   ```bash
   cd backend-java
   ./mvnw spring-boot:run
   ```

4. **Access Swagger UI:**
   - http://localhost:8080/swagger-ui.html
   - http://localhost:8080/v3/api-docs

## API Endpoints

### Authentication
- `POST /api/auth/login` - User login

### Crawler Management
- `POST /api/crawler/start` - Start a new crawl session
- `POST /api/crawler/stop/{sessionId}` - Stop a running crawl
- `GET /api/crawler/status/{sessionId}` - Get crawl status
- `GET /api/crawler` - List all sessions
- `GET /api/crawler/subreddit/{subreddit}` - Sessions by subreddit
- `GET /api/crawler/running` - List running sessions

### Data Access
- `GET /api/data/posts` - List posts with pagination
- `GET /api/data/posts/{id}` - Get post by ID
- `GET /api/data/posts/reddit/{redditId}` - Get post by Reddit ID
- `GET /api/data/posts/subreddit/{subreddit}` - Posts by subreddit
- `GET /api/data/posts/search` - Search posts
- `GET /api/data/comments` - List comments with pagination
- `GET /api/data/comments/{id}` - Get comment by ID
- `GET /api/data/comments/post/{postId}` - Comments by post

### System
- `GET /api/health` - Health check
- `GET /api/version` - API version

## Configuration

### Environment Variables

```bash
SPRING_PROFILES_ACTIVE=dev
JWT_SECRET=your-secret-key-need-to-be-at-least-256-bits-in-production
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/reddit_crawler
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
```

### Profiles

- `dev` - Development with relaxed CORS and debug logging
- `prod` - Production with secure settings and monitoring

## Building for Production

```bash
./mvnw clean package -Pprod
```

This creates a shaded JAR in `target/reddit-crawler-1.0.0-SNAPSHOT.jar`

## Docker Deployment

```bash
docker build -t reddit-crawler-api .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/reddit_crawler \
  reddit-crawler-api
```

## Testing

```bash
./mvnw test
```

## Development

### Creating New Migrations

```bash
mvn flyway:migrate
```

New migrations should be in `src/main/resources/db/migration/V__description.sql`

### API Documentation

The OpenAPI/Swagger documentation is automatically generated and available at:
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## Migration from Python FastAPI

This Spring Boot application replaces the previous Python FastAPI backend. The data model and API endpoints are designed to be compatible with the Python version for seamless migration.

## License

MIT License
