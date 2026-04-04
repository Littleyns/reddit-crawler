from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func, or_
from sqlalchemy.orm import Session

from backend.src.database.models import Comment, Post, ScrapingSession
from backend.src.database.session import get_db

router = APIRouter()


@router.get("/posts")
def list_posts(
    page: int = Query(default=1, ge=1),
    pageSize: int = Query(default=10, ge=1, le=100),
    search: str | None = Query(default=None),
    subreddit: str | None = Query(default=None),
    db: Session = Depends(get_db),
) -> dict:
    query = db.query(Post).order_by(Post.created_at.desc())
    if subreddit:
        query = query.filter(Post.subreddit == subreddit)
    if search:
        like = f"%{search.lower()}%"
        query = query.filter(
            or_(
                func.lower(Post.title).like(like),
                func.lower(Post.author).like(like),
                func.lower(Post.subreddit).like(like),
            )
        )

    total = query.count()
    offset = (page - 1) * pageSize
    posts = query.offset(offset).limit(pageSize).all()
    return {
        "items": [
            {
                "id": str(post.id),
                "title": post.title,
                "author": post.author,
                "subreddit": post.subreddit,
                "score": post.upvotes,
                "url": post.url,
                "createdAt": post.created_at.isoformat(),
                "commentsCount": len(post.comments),
            }
            for post in posts
        ],
        "page": page,
        "pageSize": pageSize,
        "total": total,
        "totalPages": max((total + pageSize - 1) // pageSize, 1),
    }


@router.get("/comments")
def list_comments(
    page: int = Query(default=1, ge=1),
    pageSize: int = Query(default=10, ge=1, le=100),
    search: str | None = Query(default=None),
    subreddit: str | None = Query(default=None),
    db: Session = Depends(get_db),
) -> dict:
    query = db.query(Comment).join(Post).order_by(Comment.created_at.desc())
    if subreddit:
        query = query.filter(Post.subreddit == subreddit)
    if search:
        like = f"%{search.lower()}%"
        query = query.filter(
            or_(
                func.lower(Comment.body).like(like),
                func.lower(Comment.author).like(like),
                func.lower(Post.subreddit).like(like),
                func.lower(Post.title).like(like),
            )
        )

    total = query.count()
    offset = (page - 1) * pageSize
    comments = query.offset(offset).limit(pageSize).all()
    return {
        "items": [
            {
                "id": str(comment.id),
                "author": comment.author,
                "body": comment.body,
                "subreddit": comment.post.subreddit,
                "score": comment.upvotes,
                "parentPostTitle": comment.post.title,
                "createdAt": comment.created_at.isoformat(),
            }
            for comment in comments
        ],
        "page": page,
        "pageSize": pageSize,
        "total": total,
        "totalPages": max((total + pageSize - 1) // pageSize, 1),
    }


@router.get("/{post_id}")
def get_post(post_id: int, db: Session = Depends(get_db)) -> dict:
    post = db.query(Post).filter(Post.id == post_id).first()
    if post is None:
        raise HTTPException(status_code=404, detail="Post not found.")

    return {
        "id": post.id,
        "reddit_id": post.reddit_id,
        "title": post.title,
        "author": post.author,
        "subreddit": post.subreddit,
        "upvotes": post.upvotes,
        "url": post.url,
        "created_at": post.created_at.isoformat(),
        "content": post.content,
        "session": {
            "id": post.scraping_session.id,
            "subreddit": post.scraping_session.subreddit,
            "sort": post.scraping_session.sort,
            "status": post.scraping_session.status,
        },
        "comments": [
            {
                "id": comment.id,
                "reddit_id": comment.reddit_id,
                "author": comment.author,
                "body": comment.body,
                "upvotes": comment.upvotes,
                "depth": comment.depth,
                "parent_comment_id": comment.parent_comment_id,
                "created_at": comment.created_at.isoformat(),
            }
            for comment in sorted(post.comments, key=lambda item: (item.depth, item.created_at))
        ],
    }
