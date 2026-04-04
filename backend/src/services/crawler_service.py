from __future__ import annotations

from datetime import datetime, timezone
from threading import Lock

from sqlalchemy.orm import Session

from backend.src.database.models import Comment, Post, ScrapingSession
from backend.src.database.session import SessionLocal
from backend.src.scrapers.reddit_scraper import RedditScraper


class CrawlerService:
    def __init__(self, scraper: RedditScraper | None = None) -> None:
        self._scraper = scraper
        self._lock = Lock()
        self._active_session_id: int | None = None
        self._stop_requested = False
        self._latest_progress = 0

    @staticmethod
    def _utcnow_naive() -> datetime:
        return datetime.now(timezone.utc).replace(tzinfo=None)

    @property
    def is_running(self) -> bool:
        with self._lock:
            return self._active_session_id is not None

    def create_session(
        self,
        subreddit: str,
        limit: int,
        sort: str,
        depth: int = 1,
        include_comments: bool = True,
        keywords: str | None = None,
    ) -> ScrapingSession:
        with SessionLocal() as db:
            session = ScrapingSession(
                subreddit=subreddit,
                post_limit=limit,
                sort=sort,
                depth=depth,
                include_comments=1 if include_comments else 0,
                keywords=keywords or None,
                status="queued",
            )
            db.add(session)
            db.commit()
            db.refresh(session)
            return session

    def request_stop(self) -> None:
        with self._lock:
            self._stop_requested = True

    @staticmethod
    def _build_config(session: ScrapingSession | None) -> dict:
        if session is None:
            return {
                "subreddit": "machinelearning",
                "depth": 1,
                "limit": 25,
                "includeComments": True,
                "keywords": "",
            }

        return {
            "subreddit": session.subreddit,
            "depth": session.depth,
            "limit": session.post_limit,
            "includeComments": bool(session.include_comments),
            "keywords": session.keywords or "",
        }

    def get_status(self) -> dict:
        with SessionLocal() as db:
            session = (
                db.query(ScrapingSession)
                .order_by(ScrapingSession.started_at.desc())
                .first()
            )

            with self._lock:
                active_session_id = self._active_session_id
                stop_requested = self._stop_requested
                progress = self._latest_progress

            if session is None:
                return {
                    "isRunning": False,
                    "currentSubreddit": None,
                    "progress": 0,
                    "mode": "idle",
                    "activeWorkers": 0,
                    "requestsPerMinute": 0,
                    "lastRunAt": "",
                    "config": self._build_config(None),
                }

            return {
                "isRunning": active_session_id is not None,
                "currentSubreddit": session.subreddit if active_session_id is not None else None,
                "progress": progress if active_session_id is not None else (100 if session.status == "completed" else 0),
                "mode": "stopping" if stop_requested else ("collecting" if active_session_id is not None else "idle"),
                "activeWorkers": 1 if active_session_id is not None else 0,
                "requestsPerMinute": 60 if active_session_id is not None else 0,
                "lastRunAt": (
                    session.finished_at.isoformat()
                    if session.finished_at
                    else session.started_at.isoformat()
                ),
                "config": self._build_config(session),
            }

    def run_session(self, session_id: int) -> None:
        with self._lock:
            self._active_session_id = session_id
            self._stop_requested = False
            self._latest_progress = 0

        with SessionLocal() as db:
            session = db.query(ScrapingSession).filter(ScrapingSession.id == session_id).one()
            session.status = "running"
            db.commit()

            try:
                scraper = self._scraper or RedditScraper()
                results = scraper.scrape_subreddit(
                    subreddit_name=session.subreddit,
                    limit=session.post_limit,
                    sort=session.sort,
                )

                total_results = len(results) or 1
                for index, result in enumerate(results, start=1):
                    if self._stop_requested:
                        session.status = "stopped"
                        session.finished_at = self._utcnow_naive()
                        db.commit()
                        return

                    self._persist_post_bundle(db, session, result)
                    with self._lock:
                        self._latest_progress = int(index / total_results * 100)

                session.status = "completed"
                session.finished_at = self._utcnow_naive()
                db.commit()
            except Exception as exc:
                session.status = "failed"
                session.error_message = str(exc)
                session.finished_at = self._utcnow_naive()
                db.commit()
                raise
            finally:
                with self._lock:
                    self._active_session_id = None
                    self._stop_requested = False
                    if session.status != "completed":
                        self._latest_progress = 0

    def _persist_post_bundle(
        self,
        db: Session,
        session: ScrapingSession,
        result: dict,
    ) -> None:
        post_data = result["post"]
        existing_post = db.query(Post).filter(Post.reddit_id == post_data["reddit_id"]).first()
        if existing_post is not None:
            return

        post = Post(
            scraping_session_id=session.id,
            reddit_id=post_data["reddit_id"],
            title=post_data["title"],
            author=post_data["author"],
            subreddit=post_data["subreddit"],
            upvotes=post_data["upvotes"],
            url=post_data["url"],
            content=post_data["content"],
            created_at=post_data["created_at"].replace(tzinfo=None),
        )
        db.add(post)
        db.flush()

        comment_id_map: dict[str, int] = {}
        for comment_data in result["comments"]:
            comment = Comment(
                reddit_id=comment_data["reddit_id"],
                post_id=post.id,
                parent_comment_id=comment_id_map.get(comment_data["parent_reddit_id"]),
                author=comment_data["author"],
                body=comment_data["body"],
                upvotes=comment_data["upvotes"],
                depth=comment_data["depth"],
                created_at=comment_data["created_at"].replace(tzinfo=None),
            )
            db.add(comment)
            db.flush()
            comment_id_map[comment_data["reddit_id"]] = comment.id

        db.commit()
