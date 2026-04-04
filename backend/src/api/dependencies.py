from fastapi import Request

from backend.src.services.crawler_service import CrawlerService


def get_crawler_service(request: Request) -> CrawlerService:
    return request.app.state.crawler_service
