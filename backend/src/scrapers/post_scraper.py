"""PostScraper module for extracting and processing Reddit posts."""

import logging
from typing import Optional, Dict, Any
from datetime import datetime

from ..scrapers.reddit_scraper import RedditScraper


logger = logging.getLogger(__name__)


class PostScraper:
    """Post scraper class for extracting post data from Reddit."""
    
    def __init__(self, reddit_scraper: RedditScraper | None = None):
        """Initialize PostScraper with a RedditScraper instance.
        
        Args:
            reddit_scraper: RedditScraper instance for API access
        """
        self.reddit_scraper = reddit_scraper
        logger.info("PostScraper initialized")

    def extract(self, submission) -> Dict[str, Any]:
        return {
            "reddit_id": submission.id,
            "title": submission.title,
            "author": getattr(getattr(submission, "author", None), "name", "[deleted]"),
            "subreddit": submission.subreddit.display_name,
            "upvotes": getattr(submission, "score", 0),
            "url": submission.url,
            "content": getattr(submission, "selftext", None),
            "created_at": datetime.fromtimestamp(submission.created_utc),
        }
    
    def scrape_post(self, post_id: str) -> Optional[Dict[str, Any]]:
        """Scrape a single Reddit post by ID.
        
        Args:
            post_id: Reddit post ID to scrape
            
        Returns:
            dict: Post data dictionary or None if not found
        """
        if not self.reddit_scraper.is_authenticated:
            logger.error("RedditScraper not authenticated")
            return None
        
        try:
            post = self.reddit_scraper._reddit.submission(id=post_id)
            
            post_data = {
                "id": post.id,
                "title": post.title,
                "author": post.author.name if post.author else "[deleted]",
                "author_fullname": post.author_fullname if post.author else None,
                "subreddit": post.subreddit.display_name,
                "subreddit_id": post.subreddit.id,
                "url": post.url,
                "created_at": datetime.fromtimestamp(post.created_utc),
                "created_timestamp": post.created_utc,
                "upvotes": post.upvotes,
                "upvote_ratio": post.upvote_ratio,
                "num_comments": post.num_comments,
                "permalink": post.permalink,
                "is_self": post.is_self,
                "is_video": post.is_video,
                "is_crosspost": post.is_crosspost,
                "selftext": post.selftext if post.is_self else None,
                "link_flair_text": getattr(post, 'link_flair_text', None),
                "link_flair_background_color": getattr(post, 'link_flair_background_color', None),
                "post_hint": getattr(post, 'post_hint', None),
                "preview": {
                    "images": getattr(post, "preview", {}).get("images", []) if hasattr(post, "preview") else []
                },
                "scraped_at": datetime.now()
            }
            
            logger.info(f"Scraped post: {post.id}")
            return post_data
            
        except Exception as e:
            logger.error(f"Error scraping post {post_id}: {e}")
            return None
    
    def scrape_subreddit_posts(
        self,
        subreddit: str,
        limit: int = 25,
        sort: str = "hot",
        additional_fields: bool = True
    ) -> list:
        """Scrape multiple posts from a subreddit.
        
        Args:
            subreddit: Subreddit name
            limit: Number of posts to scrape
            sort: Sort order (hot, new, top, rising)
            additional_fields: Whether to include extra metadata
            
        Returns:
            list: List of post data dictionaries
        """
        if not self.reddit_scraper.is_authenticated:
            logger.error("RedditScraper not authenticated")
            return []
        
        try:
            posts = self.reddit_scraper.get_subreddit_posts(subreddit, limit, sort)
            
            if additional_fields:
                for post in posts:
                    post["scraped_at"] = datetime.now()
            
            logger.info(f"Scraped {len(posts)} posts from r/{subreddit}")
            return posts
            
        except Exception as e:
            logger.error(f"Error scraping subreddit {subreddit}: {e}")
            return []
    
    def scrape_search_posts(
        self,
        subreddit: str,
        query: str,
        limit: int = 25
    ) -> list:
        """Scrape posts matching a search query in a subreddit.
        
        Args:
            subreddit: Subreddit name
            query: Search query
            limit: Maximum number of posts
            
        Returns:
            list: List of post data dictionaries
        """
        if not self.reddit_scraper.is_authenticated:
            logger.error("RedditScraper not authenticated")
            return []
        
        try:
            posts = self.reddit_scraper.search_subreddit_posts(subreddit, query, limit)
            
            for post in posts:
                post["scraped_at"] = datetime.now()
            
            logger.info(f"Scraped {len(posts)} posts matching '{query}' in r/{subreddit}")
            return posts
            
        except Exception as e:
            logger.error(f"Error searching posts in r/{subreddit}: {e}")
            return []
    
    def get_post_metadata(self, post_id: str) -> Dict[str, Any]:
        """Get metadata about a post.
        
        Args:
            post_id: Reddit post ID
            
        Returns:
            dict: Post metadata dictionary
        """
        try:
            post = self.reddit_scraper._reddit.submission(id=post_id)
            
            return {
                "id": post.id,
                "title": post.title,
                "score": post.score,
                "upvote_ratio": post.upvote_ratio,
                "num_comments": post.num_comments,
                "created_utc": datetime.fromtimestamp(post.created_utc),
                "author": post.author.name if post.author else "[deleted]",
                "subreddit": post.subreddit.display_name,
                "url": post.url,
                "permalink": post.permalink,
                "is_self": post.is_self,
                "is_video": post.is_video,
                "is_crosspost": post.is_crosspost,
                "over_18": post.over_18,
                "spoiler": post.spoiler,
                "locked": post.locked,
                "archived": post.archived
            }
            
        except Exception as e:
            logger.error(f"Error getting metadata for post {post_id}: {e}")
            return {}
