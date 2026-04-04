from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


def utcnow_naive() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


class ScrapingSession(Base):
    __tablename__ = "scraping_sessions"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    subreddit: Mapped[str] = mapped_column(String(100), index=True)
    sort: Mapped[str] = mapped_column(String(20), default="hot")
    post_limit: Mapped[int] = mapped_column(Integer, default=25)
    depth: Mapped[int] = mapped_column(Integer, default=1)
    include_comments: Mapped[int] = mapped_column(Integer, default=1)
    keywords: Mapped[str | None] = mapped_column(String(255), nullable=True)
    status: Mapped[str] = mapped_column(String(20), default="pending", index=True)
    started_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow_naive)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    error_message: Mapped[str | None] = mapped_column(Text, nullable=True)

    posts: Mapped[list["Post"]] = relationship(
        back_populates="scraping_session",
        cascade="all, delete-orphan",
    )


class Post(Base):
    __tablename__ = "posts"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    reddit_id: Mapped[str] = mapped_column(String(20), unique=True, index=True)
    scraping_session_id: Mapped[int] = mapped_column(
        ForeignKey("scraping_sessions.id"),
        index=True,
    )
    title: Mapped[str] = mapped_column(String(500))
    author: Mapped[str] = mapped_column(String(100), default="[deleted]")
    subreddit: Mapped[str] = mapped_column(String(100), index=True)
    upvotes: Mapped[int] = mapped_column(Integer, default=0)
    url: Mapped[str] = mapped_column(String(1000))
    content: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, index=True)

    scraping_session: Mapped[ScrapingSession] = relationship(back_populates="posts")
    comments: Mapped[list["Comment"]] = relationship(
        back_populates="post",
        cascade="all, delete-orphan",
    )


class Comment(Base):
    __tablename__ = "comments"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    reddit_id: Mapped[str] = mapped_column(String(20), unique=True, index=True)
    post_id: Mapped[int] = mapped_column(ForeignKey("posts.id"), index=True)
    parent_comment_id: Mapped[int | None] = mapped_column(
        ForeignKey("comments.id"),
        nullable=True,
        index=True,
    )
    author: Mapped[str] = mapped_column(String(100), default="[deleted]")
    body: Mapped[str] = mapped_column(Text)
    upvotes: Mapped[int] = mapped_column(Integer, default=0)
    depth: Mapped[int] = mapped_column(Integer, default=0, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, index=True)

    post: Mapped[Post] = relationship(back_populates="comments")
    parent: Mapped["Comment | None"] = relationship(remote_side=[id])
