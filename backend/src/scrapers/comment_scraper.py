from __future__ import annotations

from datetime import datetime, timezone
from typing import Any


class CommentScraper:
    def extract(self, comments: list[Any]) -> list[dict[str, Any]]:
        extracted: list[dict[str, Any]] = []
        for comment in comments:
            extracted.extend(self._extract_comment(comment, parent_reddit_id=None, depth=0))
        return extracted

    def _extract_comment(
        self,
        comment: Any,
        parent_reddit_id: str | None,
        depth: int,
    ) -> list[dict[str, Any]]:
        if getattr(comment, "body", None) is None:
            return []

        payload = {
            "reddit_id": comment.id,
            "parent_reddit_id": parent_reddit_id,
            "author": getattr(getattr(comment, "author", None), "name", "[deleted]"),
            "body": comment.body,
            "upvotes": getattr(comment, "score", 0),
            "depth": depth,
            "created_at": datetime.fromtimestamp(comment.created_utc, tz=timezone.utc),
        }

        flattened = [payload]
        for reply in getattr(comment, "replies", []):
            flattened.extend(
                self._extract_comment(
                    reply,
                    parent_reddit_id=comment.id,
                    depth=depth + 1,
                )
            )

        return flattened
