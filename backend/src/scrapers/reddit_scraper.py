"""RedditScraper module for handling Reddit API authentication and subreddit scraping."""

import logging
import os
from datetime import datetime
from typing import Any, Optional

import praw

from backend.src.scrapers.comment_scraper import CommentScraper


logger = logging.getLogger(__name__)


class RedditScraper:
    """Main Reddit scraper class handling authentication and subreddit scraping."""
    
    def __init__(
        self,
        client_id: str | None = None,
        client_secret: str | None = None,
        user_agent: str | None = None,
        username: Optional[str] = None,
        password: Optional[str] = None,
        reddit_client: Any | None = None,
    ):
        """Initialize RedditScraper with authentication credentials.
        
        Args:
            client_id: Reddit client ID for API access
            client_secret: Reddit client secret for API access
            user_agent: Custom user agent string for the application
            username: Optional username for user-based authentication
            password: Optional password for user-based authentication
        """
        self.client_id = client_id or os.getenv("REDDIT_CLIENT_ID", "")
        self.client_secret = client_secret or os.getenv("REDDIT_CLIENT_SECRET", "")
        self.user_agent = user_agent or os.getenv("REDDIT_USER_AGENT", "reddit-crawler/1.0")
        self.username = username
        self.password = password

        self._reddit: Optional[praw.Reddit] = reddit_client
        self._authenticated = reddit_client is not None
    
    def authenticate(self) -> bool:
        """Authenticate with Reddit API.
        
        Returns:
            bool: True if authentication successful, False otherwise
        """
        try:
            if self.username and self.password:
                # User credentials authentication
                self._reddit = praw.Reddit(
                    client_id=self.client_id,
                    client_secret=self.client_secret,
                    username=self.username,
                    password=self.password,
                    user_agent=self.user_agent
                )
            else:
                # Anonymous authentication
                self._reddit = praw.Reddit(
                    client_id=self.client_id,
                    client_secret=self.client_secret,
                    user_agent=self.user_agent
                )
            
            # Verify authentication by accessing user when available.
            if hasattr(self._reddit, "user"):
                self._reddit.user.me()
            self._authenticated = True
            logger.info("Successfully authenticated with Reddit API")
            return True
            
        except Exception as e:
            logger.error(f"Authentication failed: {e}")
            self._authenticated = False
            return False
    
    @property
    def is_authenticated(self) -> bool:
        """Check if currently authenticated."""
        return self._authenticated and self._reddit is not None
    
    def get_subreddit(self, subreddit_name: str):
        """Get a subreddit object.
        
        Args:
            subreddit_name: Name of the subreddit
            
        Returns:
            praw.Subreddit: Subreddit object or None if not found
        """
        if not self.is_authenticated:
            logger.error("Not authenticated with Reddit API")
            return None
        
        try:
            return self._reddit.subreddit(subreddit_name)
        except Exception as e:
            logger.error(f"Error fetching subreddit {subreddit_name}: {e}")
            return None

    def _ensure_client(self) -> bool:
        if self.is_authenticated:
            return True

        return self.authenticate()
    
    def get_subreddit_posts(
        self,
        subreddit_name: str,
        limit: int = 25,
        sort: str = "hot"
    ):
        """Get posts from a subreddit.
        
        Args:
            subreddit_name: Name of the subreddit
            limit: Maximum number of posts to retrieve (max 1000)
            sort: Sort order (hot, new, top, rising)
            
        Returns:
            list: List of post dictionaries or empty list on error
        """
        if not self._ensure_client():
            logger.error("Not authenticated with Reddit API")
            return []
        
        try:
            subreddit = self.get_subreddit(subreddit_name)
            if not subreddit:
                return []
            
            # Map sort parameter
            sort_methods = {
                "hot": "hot",
                "new": "new",
                "top": "top",
                "rising": "rising"
            }
            
            sort_method = sort_methods.get(sort, "hot")
            posts = []
            
            # Get posts using the subreddit method
            post_generator = getattr(subreddit, sort_method)(limit=min(limit, 1000))
            
            for post in post_generator:
                posts.append({
                    "id": post.id,
                    "title": post.title,
                    "author": post.author.name if post.author else "[deleted]",
                    "subreddit": post.subreddit.display_name,
                    "url": post.url,
                    "created_at": datetime.fromtimestamp(post.created_utc),
                    "upvotes": post.upvote_ratio * post.upvotes if post.upvote_ratio else post.upvotes,
                    "num_comments": post.num_comments,
                    "permalink": post.permalink,
                    "is_self": post.is_self,
                    "selftext": post.selftext if post.is_self else None
                })
            
            logger.info(f"Retrieved {len(posts)} posts from r/{subreddit_name}")
            return posts
            
        except Exception as e:
            logger.error(f"Error fetching posts from r/{subreddit_name}: {e}")
            return []
    
    def search_subreddit_posts(
        self,
        subreddit_name: str,
        query: str,
        limit: int = 25
    ):
        """Search for posts in a subreddit.
        
        Args:
            subreddit_name: Name of the subreddit
            query: Search query string
            limit: Maximum number of posts to retrieve
            
        Returns:
            list: List of post dictionaries
        """
        if not self._ensure_client():
            logger.error("Not authenticated with Reddit API")
            return []
        
        try:
            subreddit = self.get_subreddit(subreddit_name)
            if not subreddit:
                return []
            
            posts = []
            search_results = subreddit.search(query, limit=min(limit, 1000))
            
            for post in search_results:
                posts.append({
                    "id": post.id,
                    "title": post.title,
                    "author": post.author.name if post.author else "[deleted]",
                    "subreddit": post.subreddit.display_name,
                    "url": post.url,
                    "created_at": datetime.fromtimestamp(post.created_utc),
                    "upvotes": post.upvote_ratio * post.upvotes if post.upvote_ratio else post.upvotes,
                    "num_comments": post.num_comments,
                    "permalink": post.permalink,
                    "is_self": post.is_self,
                    "selftext": post.selftext if post.is_self else None
                })
            
            return posts
            
        except Exception as e:
            logger.error(f"Error searching r/{subreddit_name} for '{query}': {e}")
            return []
    
    def get_post_comments(self, post_id: str, depth: int = 1, max_comments: int = 100):
        """Get comments from a post.
        
        Args:
            post_id: Reddit post ID
            depth: Maximum comment depth to retrieve (1=only top-level)
            max_comments: Maximum total comments to retrieve
            
        Returns:
            list: List of comment dictionaries
        """
        if not self._ensure_client():
            logger.error("Not authenticated with Reddit API")
            return []
        
        try:
            post = self._reddit.submission(id=post_id)
            post.comments.replace_more(limit=0)  # Disable CommentMore objects
            
            comments = []
            
            def extract_comments(comment, current_depth=0):
                if len(comments) >= max_comments or current_depth >= depth:
                    return
                
                comments.append({
                    "id": comment.id,
                    "parent_id": comment.parent_id,
                    "author": comment.author.name if comment.author else "[deleted]",
                    "body": comment.body,
                    "created_at": datetime.fromtimestamp(comment.created_utc),
                    "upvotes": comment.score,
                    "permalink": comment.permalink
                })
                
                # Recursively get replies
                for reply in comment.replies:
                    if isinstance(reply, praw.models.Comment):
                        extract_comments(reply, current_depth + 1)
            
            for comment in post.comments.list():
                if len(comments) >= max_comments:
                    break
                extract_comments(comment)
            
            return comments
            
        except Exception as e:
            logger.error(f"Error fetching comments for post {post_id}: {e}")
            return []

    def scrape_subreddit(self, subreddit_name: str, limit: int = 25, sort: str = "hot") -> list[dict]:
        if not self._ensure_client():
            return []

        subreddit = self.get_subreddit(subreddit_name)
        if subreddit is None:
            return []

        sort_method = getattr(subreddit, sort, None) or subreddit.hot
        comment_scraper = CommentScraper()
        bundles: list[dict] = []

        for submission in sort_method(limit=min(limit, 1000)):
            comments_root = getattr(submission, "comments", [])
            if hasattr(comments_root, "replace_more"):
                comments_root.replace_more(limit=0)

            bundles.append(
                {
                    "post": {
                        "reddit_id": submission.id,
                        "title": submission.title,
                        "author": getattr(getattr(submission, "author", None), "name", "[deleted]"),
                        "subreddit": submission.subreddit.display_name,
                        "upvotes": getattr(submission, "score", 0),
                        "url": submission.url,
                        "content": getattr(submission, "selftext", None),
                        "created_at": datetime.fromtimestamp(submission.created_utc),
                    },
                    "comments": comment_scraper.extract(list(comments_root)),
                }
            )

        return bundles
    
    def shutdown(self):
        """Shutdown the Reddit instance."""
        if self._reddit:
            self._reddit.__exit__(None, None, None)
            self._reddit = None
            self._authenticated = False
            logger.info("RedditScraper shutdown complete")
