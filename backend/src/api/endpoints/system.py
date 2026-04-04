import csv
import io
import json
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, Field
from sqlalchemy import func
from sqlalchemy.orm import Session

from backend.src.database.models import Comment, Post, ScrapingSession
from backend.src.database.session import get_db

router = APIRouter()

DATA_DIR = Path(__file__).resolve().parents[3] / "data"
SETTINGS_PATH = DATA_DIR / "settings.json"

DEFAULT_SETTINGS = {
    "apiKey": "reddit-api-demo-key",
    "defaultSubreddit": "machinelearning",
    "defaultDepth": 2,
    "defaultLimit": 25,
    "autoExport": False,
    "exportFormat": "json",
    "sessionTimeoutMinutes": 45,
    "users": [
        {
            "id": "1",
            "name": "Amina Hassan",
            "email": "amina@arabtooling.com",
            "role": "admin",
        },
        {
            "id": "2",
            "name": "Omar Rahman",
            "email": "omar@arabtooling.com",
            "role": "analyst",
        },
    ],
}


class LoginRequest(BaseModel):
    email: str
    password: str = Field(..., min_length=8)


class SettingsPayload(BaseModel):
    apiKey: str = Field(..., min_length=8)
    defaultSubreddit: str = Field(..., min_length=2)
    defaultDepth: int = Field(..., ge=1, le=10)
    defaultLimit: int = Field(..., ge=10, le=1000)
    autoExport: bool
    exportFormat: str = Field(..., pattern="^(csv|json)$")
    sessionTimeoutMinutes: int = Field(..., ge=5, le=240)
    users: list[dict]


def ensure_settings() -> dict:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    if not SETTINGS_PATH.exists():
        SETTINGS_PATH.write_text(json.dumps(DEFAULT_SETTINGS, indent=2), encoding="utf-8")
        return DEFAULT_SETTINGS.copy()

    return json.loads(SETTINGS_PATH.read_text(encoding="utf-8"))


def write_settings(payload: dict) -> dict:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    SETTINGS_PATH.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    return payload


@router.get("/stats")
def stats(db: Session = Depends(get_db)) -> dict:
    total_posts = db.query(func.count(Post.id)).scalar() or 0
    total_comments = db.query(func.count(Comment.id)).scalar() or 0
    total_sessions = db.query(func.count(ScrapingSession.id)).scalar() or 0
    active_subreddits = db.query(func.count(func.distinct(Post.subreddit))).scalar() or 0
    latest_sessions = (
        db.query(ScrapingSession)
        .order_by(ScrapingSession.started_at.desc())
        .limit(5)
        .all()
    )
    completed_sessions = [session for session in latest_sessions if session.status == "completed"]
    success_rate = int((len(completed_sessions) / len(latest_sessions)) * 100) if latest_sessions else 0

    return {
        "totalPosts": total_posts,
        "totalComments": total_comments,
        "totalSessions": total_sessions,
        "activeSubreddits": active_subreddits,
        "successRate": success_rate,
        "queueDepth": max(total_sessions - len(completed_sessions), 0),
        "activities": [
            {
                "id": str(session.id),
                "title": f"r/{session.subreddit} crawl {session.status}",
                "description": f"Sort: {session.sort}, limit: {session.post_limit}",
                "occurredAt": (
                    session.finished_at.isoformat()
                    if session.finished_at
                    else session.started_at.isoformat()
                ),
                "status": (
                    "success"
                    if session.status == "completed"
                    else "error" if session.status == "failed" else "running"
                ),
            }
            for session in latest_sessions
        ],
    }


@router.get("/settings")
def get_settings() -> dict:
    return ensure_settings()


@router.post("/settings")
def save_settings(payload: SettingsPayload) -> dict:
    return write_settings(payload.model_dump())


@router.post("/auth/login")
def login(payload: LoginRequest) -> dict:
    settings = ensure_settings()
    user = next((user for user in settings["users"] if user["email"] == payload.email), None)
    if user is None:
        raise HTTPException(status_code=401, detail="Invalid credentials.")

    return {
        "user": user,
        "sessionExpiresAt": "2099-12-31T23:59:59Z",
    }


@router.get("/data/export")
def export_data(
    format: str = Query(..., pattern="^(csv|json)$"),
    type: str = Query(..., pattern="^(posts|comments)$"),
    search: str | None = None,
    subreddit: str | None = None,
    db: Session = Depends(get_db),
):
    if type == "posts":
        query = db.query(Post).order_by(Post.created_at.desc())
        if subreddit:
            query = query.filter(Post.subreddit == subreddit)
        if search:
            like = f"%{search.lower()}%"
            query = query.filter(
                func.lower(Post.title).like(like)
                | func.lower(Post.author).like(like)
                | func.lower(Post.subreddit).like(like)
            )
        rows = [
            {
                "id": post.id,
                "title": post.title,
                "author": post.author,
                "subreddit": post.subreddit,
                "score": post.upvotes,
                "commentsCount": len(post.comments),
                "createdAt": post.created_at.isoformat(),
                "url": post.url,
            }
            for post in query.all()
        ]
    else:
        query = db.query(Comment, Post.title).join(Post, Comment.post_id == Post.id)
        if subreddit:
            query = query.filter(Post.subreddit == subreddit)
        if search:
            like = f"%{search.lower()}%"
            query = query.filter(
                func.lower(Comment.body).like(like)
                | func.lower(Comment.author).like(like)
                | func.lower(Post.title).like(like)
                | func.lower(Post.subreddit).like(like)
            )
        rows = [
            {
                "id": comment.id,
                "author": comment.author,
                "subreddit": comment.post.subreddit,
                "body": comment.body,
                "score": comment.upvotes,
                "parentPostTitle": post_title,
                "createdAt": comment.created_at.isoformat(),
            }
            for comment, post_title in query.all()
        ]

    if format == "json":
        return JSONResponse(content=rows)

    buffer = io.StringIO()
    writer = csv.DictWriter(buffer, fieldnames=list(rows[0].keys()) if rows else ["id"])
    writer.writeheader()
    writer.writerows(rows)
    return StreamingResponse(
        iter([buffer.getvalue()]),
        media_type="text/csv",
        headers={"Content-Disposition": f'attachment; filename="{type}.csv"'},
    )
