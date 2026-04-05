"""Reddit Crawler Backend - Main Application

FastAPI application entry point with route configuration.
"""
from contextlib import asynccontextmanager
from typing import AsyncGenerator
from fastapi import FastAPI, Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.openapi.utils import get_openapi

from src.config import settings
from src.api.dependencies import get_db
from src.database import SessionLocal
from src.api.endpoints import crawler, data

# Application lifespan
@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Application lifespan handler."""
    # Startup
    yield
    # Shutdown (cleanup if needed)


# Initialize FastAPI app
app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="API for Reddit crawler application with real-time monitoring and data export.",
    lifespan=lifespan,
    docs_url="/docs" if settings.debug else None,
    redoc_url="/redoc" if settings.debug else None,
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure appropriately for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Exception handler for validation errors
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    """Handle all uncaught exceptions."""
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal server error", "error": str(exc)},
    )


# Custom exception handler
@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    """Handle HTTP exceptions."""
    return JSONResponse(
        status_code=exc.status_code,
        content={"detail": exc.detail},
    )


# Include routers
app.include_router(crawler.router, prefix="/api/crawler", tags=["crawler"])
app.include_router(data.router, prefix="/api/data", tags=["data"])


@app.get("/", tags=["health"])
async def root():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "app": settings.app_name,
        "version": settings.app_version,
        "environment": settings.environment,
    }


@app.get("/health", tags=["health"])
async def health_check():
    """Detailed health check."""
    try:
        db = next(get_db())
        db.execute("SELECT 1")  # Simple database check
        db.close()
        db_status = "connected"
    except Exception as e:
        db_status = f"disconnected: {e}"

    return {
        "status": "healthy",
        "app": settings.app_name,
        "version": settings.app_version,
        "environment": settings.environment,
        "database": db_status,
    }


def custom_openapi():
    """Customize OpenAPI schema."""
    if app.openapi_schema:
        return app.openapi_schema
    openapi_schema = get_openapi(
        title=settings.app_name,
        version=settings.app_version,
        description="""
## Features

- **Real-time monitoring**: Track scraping sessions and crawler status
- **Data retrieval**: Access crawled posts and comments with pagination
- **Export capabilities**: Download data in various formats
- **Async scraping**: Non-blocking Reddit data extraction using PRAW
- **Database storage**: Persistent storage with PostgreSQL

## Authentication

Currently unauthenticated for development. Add authentication for production use.

## Rate Limiting

API requests are limited to {rate_limit_requests_per_minute} per minute.
        """.strip(),
        routes=app.routes,
    )
    app.openapi_schema = openapi_schema
    return openapi_schema


app.openapi = custom_openapi  # type: ignore
