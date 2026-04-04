from datetime import datetime, timezone

from fastapi.testclient import TestClient

from backend.main import app
from backend.src.database.models import Comment, Post, ScrapingSession
from backend.src.database.session import SessionLocal, init_db


def seed_database() -> None:
    init_db()
    with SessionLocal() as db:
        db.query(Comment).delete()
        db.query(Post).delete()
        db.query(ScrapingSession).delete()
        db.commit()

        session = ScrapingSession(
            subreddit="python",
            sort="hot",
            post_limit=10,
            status="completed",
        )
        db.add(session)
        db.flush()

        post = Post(
            reddit_id="abc123",
            scraping_session_id=session.id,
            title="Example post",
            author="tester",
            subreddit="python",
            upvotes=42,
            url="https://reddit.com/r/python/example",
            content="body",
            created_at=datetime(2024, 1, 1, tzinfo=timezone.utc).replace(tzinfo=None),
        )
        db.add(post)
        db.flush()

        db.add(
            Comment(
                reddit_id="c1",
                post_id=post.id,
                parent_comment_id=None,
                author="commenter",
                body="first",
                upvotes=5,
                depth=0,
                created_at=datetime(2024, 1, 1, tzinfo=timezone.utc).replace(tzinfo=None),
            )
        )
        db.commit()


def test_list_posts_returns_seeded_post():
    seed_database()
    with TestClient(app) as client:
        response = client.get("/api/data/posts")

    assert response.status_code == 200
    body = response.json()
    assert body["total"] == 1
    assert body["items"][0]["title"] == "Example post"
    assert body["items"][0]["commentsCount"] == 1


def test_get_post_returns_comments():
    seed_database()
    with TestClient(app) as client:
        posts_response = client.get("/api/data/posts")
        post_id = posts_response.json()["items"][0]["id"]
        response = client.get(f"/api/data/{post_id}")

    assert response.status_code == 200
    body = response.json()
    assert body["reddit_id"] == "abc123"
    assert len(body["comments"]) == 1


def test_stats_endpoint_reports_seeded_counts():
    seed_database()
    with TestClient(app) as client:
        response = client.get("/api/stats")

    assert response.status_code == 200
    body = response.json()
    assert body["totalPosts"] == 1
    assert body["totalComments"] == 1
