"""Reddit Crawler Backend - Database Models

SQLAlchemy ORM models for Post, Comment, and ScrapingSession tables.
"""
from datetime import datetime
from typing import Optional, List, TYPE_CHECKING

from sqlalchemy import (
    Column,
    Integer,
    String,
    Text,
    Boolean,
    DateTime,
    ForeignKey,
    Index,
    func,
)
from sqlalchemy.orm import relationship, declared_attr, Mapped, mapped_column

from src.database import engine

# Base model class
class Base:
    """Base model with common attributes."""
    
    @declared_attr
    def __tablename__(cls) -> str:
        return cls.__name__.lower()

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, default=datetime.utcnow, index=True
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        index=True,
    )


# Post model
class Post(Base):
    """Reddit post model."""

    __tablename__ = "posts"

    # Reddit metadata
    post_id: Mapped[str] = mapped_column(
        String(50), unique=True, index=True, nullable=False
    )
    title: Mapped[str] = mapped_column(String(500), nullable=False)
    url: Mapped[str] = mapped_column(String(2000), nullable=True)
    author: Mapped[str] = mapped_column(String(100), index=True, nullable=True)
    subreddit: Mapped[str] = mapped_column(String(100), index=True, nullable=False)
    selftext: Mapped[str] = mapped_column(Text, nullable=True, default="")
    post_type: Mapped[str] = mapped_column(String(50), nullable=False)

    # Statistics
    score: Mapped[int] = mapped_column(Integer, default=0)
    upvote_ratio: Mapped[float] = mapped_column(
        Float, nullable=True
    )  # Range: 0.0 to 1.0
    num_comments: Mapped[int] = mapped_column(Integer, default=0)
    permalink: Mapped[str] = mapped_column(String(500), nullable=True)

    # Link metadata
    is_self: Mapped[bool] = mapped_column(Boolean, default=False)
    is_video: Mapped[bool] = mapped_column(Boolean, default=False)
    is_original_content: Mapped[bool] = mapped_column(Boolean, default=False)
    over_18: Mapped[bool] = mapped_column(Boolean, default=False)
    stickied: Mapped[bool] = mapped_column(Boolean, default=False)

    # Engagement tracking
    first_crawled_at: Mapped[Optional[datetime]] = mapped_column(
        DateTime, index=True
    )
    last_crawled_at: Mapped[Optional[datetime]] = mapped_column(DateTime)

    # Relationships
    comments: Mapped[List["Comment"]] = relationship(
        "Comment", back_populates="post", lazy="dynamic", cascade="all, delete-orphan"
    )

    # Indexes
    __table_args__ = (
        Index("idx_post_subreddit_created", "subreddit", "created_at"),
        Index("idx_post_score", "score", "created_at"),
    )


# Comment model
class Comment(Base):
    """Reddit comment model."""

    __tablename__ = "comments"

    # Reddit metadata
    comment_id: Mapped[str] = mapped_column(
        String(50), unique=True, index=True, nullable=False
    )
    parent_id: Mapped[Optional[str]] = mapped_column(String(50), index=True)
    parent_type: Mapped[Optional[str]] = mapped_column(
        String(20), nullable=True
    )  # 'post' or 'comment'
    body: Mapped[str] = mapped_column(Text, nullable=False)
    author: Mapped[Optional[str]] = mapped_column(String(100), index=True)
    subreddit: Mapped[str] = mapped_column(String(100), index=True, nullable=False)
    depth: Mapped[int] = mapped_column(Integer, default=0)

    # Post relationship
    post_id: Mapped[Optional[str]] = mapped_column(
        String(50), ForeignKey("posts.post_id"), nullable=True
    )

    # Statistics
    score: Mapped[int] = mapped_column(Integer, default=0)
    liked: Mapped[Optional[bool]] = mapped_column(Boolean)

    # Relationship tracking
    first_crawled_at: Mapped[Optional[datetime]] = mapped_column(DateTime, index=True)
    last_crawled_at: Mapped[Optional[datetime]] = mapped_column(DateTime)

    # Relationships
    post: Mapped["Post"] = relationship("Post", back_populates="comments")
    parent: Mapped[Optional["Comment"]] = relationship(
        "Comment",
        remote_side="Comment.comment_id",
        backref="replies",
        primaryjoin="Comment.parent_id == Comment.comment_id",
    )

    # Indexes
    __table_args__ = (
        Index("idx_comment_post_created", "post_id", "created_at"),
        Index("idx_comment_subreddit_created", "subreddit", "created_at"),
    )


# ScrapingSession model
class ScrapingSession(Base):
    """Track scraping session metadata."""

    __tablename__ = "scraping_sessions"

    # Session metadata
    session_id: Mapped[str] = mapped_column(
        String(50), unique=True, index=True, nullable=False
    )
    status: Mapped[str] = mapped_column(
        String(20), nullable=False, default="pending"
    )  # pending, running, completed, failed, stopped

    # Configuration
    subreddits: Mapped[str] = mapped_column(Text, nullable=False)
    max_posts: Mapped[int] = mapped_column(Integer, default=100)
    max_comments_per_post: Mapped[int] = mapped_column(Integer, default=50)
    started_at: Mapped[Optional[datetime]] = mapped_column(DateTime)
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime)

    # Results
    posts_found: Mapped[int] = mapped_column(Integer, default=0)
    posts_scraped: Mapped[int] = mapped_column(Integer, default=0)
    comments_scraped: Mapped[int] = mapped_column(Integer, default=0)
    error_message: Mapped[Optional[str]] = mapped_column(Text)

    def to_dict(self) -> dict:
        """Convert session to dictionary."""
        return {
            "session_id": self.session_id,
            "status": self.status,
            "subreddits": self.subreddits,
            "max_posts": self.max_posts,
            "max_comments_per_post": self.max_comments_per_post,
            "started_at": self.started_at.isoformat() if self.started_at else None,
            "completed_at": self.completed_at.isoformat() if self.completed_at else None,
            "posts_found": self.posts_found,
            "posts_scraped": self.posts_scraped,
            "comments_scraped": self.comments_scraped,
            "error_message": self.error_message,
        }


# Create all tables
Base.metadata.create_all(bind=engine)
