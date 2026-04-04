from types import SimpleNamespace

from backend.src.scrapers.comment_scraper import CommentScraper
from backend.src.scrapers.post_scraper import PostScraper
from backend.src.scrapers.reddit_scraper import RedditScraper


class FakeCommentForest(list):
    def replace_more(self, limit: int = 0) -> None:
        return None


class FakeSubmission:
    def __init__(self):
        self.id = "post1"
        self.title = "Hello Reddit"
        self.author = SimpleNamespace(name="alice")
        self.subreddit = SimpleNamespace(display_name="python")
        self.score = 123
        self.created_utc = 1712232000
        self.url = "https://reddit.com/post1"
        self.selftext = "Some content"
        child_comment = SimpleNamespace(
            id="comment2",
            body="child",
            author=SimpleNamespace(name="carol"),
            score=2,
            created_utc=1712232020,
            replies=[],
        )
        root_comment = SimpleNamespace(
            id="comment1",
            body="root",
            author=SimpleNamespace(name="bob"),
            score=10,
            created_utc=1712232010,
            replies=[child_comment],
        )
        self.comments = FakeCommentForest([root_comment])


class FakeSubreddit:
    def __init__(self):
        self._items = [FakeSubmission()]

    def hot(self, limit: int):
        return self._items[:limit]

    def new(self, limit: int):
        return self._items[:limit]

    def top(self, limit: int):
        return self._items[:limit]

    def rising(self, limit: int):
        return self._items[:limit]


class FakeReddit:
    def subreddit(self, name: str):
        assert name == "python"
        return FakeSubreddit()


def test_post_scraper_extracts_submission_fields():
    payload = PostScraper().extract(FakeSubmission())

    assert payload["reddit_id"] == "post1"
    assert payload["author"] == "alice"
    assert payload["subreddit"] == "python"
    assert payload["content"] == "Some content"


def test_comment_scraper_flattens_nested_comments():
    comments = CommentScraper().extract(list(FakeSubmission().comments))

    assert len(comments) == 2
    assert comments[0]["reddit_id"] == "comment1"
    assert comments[1]["parent_reddit_id"] == "comment1"
    assert comments[1]["depth"] == 1


def test_reddit_scraper_uses_client_and_combines_post_with_comments():
    scraper = RedditScraper(reddit_client=FakeReddit())

    results = scraper.scrape_subreddit("python", limit=5, sort="hot")

    assert len(results) == 1
    assert results[0]["post"]["title"] == "Hello Reddit"
    assert len(results[0]["comments"]) == 2
