"""Reddit Crawler Backend - Configuration Module

Pydantic settings for managing application configuration with environment variables.
"""
from functools import lru_cache
from typing import Optional

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    model_config = SettingsConfigDict(
        env_file=".env", env_file_encoding="utf-8", case_sensitive=False, extra="ignore"
    )

    # Application
    app_name: str = "Reddit Crawler API"
    app_version: str = "1.0.0"
    debug: bool = False

    # Database
    database_url: str = Field(
        default="postgresql://postgres:postgres@localhost:5432/reddit_crawler",
        description="PostgreSQL database connection URL",
    )

    # PRAW Reddit API
    praw_client_id: str = Field(
        default="", description="Reddit API client ID"
    )
    praw_client_secret: str = Field(
        default="", description="Reddit API client secret"
    )
    praw_user_agent: str = Field(
        default="reddit-crawler:1.0.0:v1.0 (by /u/reddit-crawler)",
        description="User agent for Reddit API",
    )

    # Crawler settings
    default_subreddits: str = Field(
        default="python,reactjs,webdev,learnprogramming",
        description="Comma-separated list of default subreddits to monitor",
    )
    max_posts_per_session: int = Field(
        default=100, ge=1, le=1000, description="Maximum posts per scraping session"
    )
    max_comments_per_post: int = Field(
        default=50, ge=1, le=500, description="Maximum comments to scrape per post"
    )
    scrape_interval_seconds: int = Field(
        default=300, ge=60, le=3600, description="Interval between scrapes in seconds"
    )

    # Rate limiting
    rate_limit_requests_per_minute: int = Field(
        default=60, ge=1, le=1000, description="Maximum API requests per minute"
    )

    @field_validator("praw_client_id", "praw_client_secret")
    @classmethod
    def validate_reddit_credentials(cls, v, info):
        """Validate Reddit credentials are provided in production."""
        if not v and info.data.get("environment") != "development":
            raise ValueError(
                f"{info.field_name} is required when not in development environment"
            )
        return v

    @property
    def environment(self) -> str:
        """Return environment name based on debug setting."""
        return "development" if self.debug else "production"


@lru_cache
def get_settings() -> Settings:
    """Cached settings instance."""
    return Settings()


settings = get_settings()
