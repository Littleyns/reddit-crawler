from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, status
from pydantic import BaseModel, Field

from backend.src.api.dependencies import get_crawler_service
from backend.src.services.crawler_service import CrawlerService

router = APIRouter()


class StartCrawlerRequest(BaseModel):
    subreddit: str = Field(..., min_length=2, max_length=100)
    limit: int = Field(default=25, ge=1, le=100)
    depth: int = Field(default=1, ge=1, le=10)
    includeComments: bool = True
    keywords: str | None = Field(default=None, max_length=255)
    sort: str = Field(default="hot", pattern="^(hot|new|top|rising)$")


class StopCrawlerResponse(BaseModel):
    detail: str


@router.post("/start", status_code=status.HTTP_202_ACCEPTED)
def start_crawler(
    payload: StartCrawlerRequest,
    background_tasks: BackgroundTasks,
    service: CrawlerService = Depends(get_crawler_service),
) -> dict:
    if service.is_running:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A crawl is already in progress.",
        )

    session = service.create_session(
        subreddit=payload.subreddit,
        limit=payload.limit,
        sort=payload.sort,
        depth=payload.depth,
        include_comments=payload.includeComments,
        keywords=payload.keywords,
    )
    background_tasks.add_task(service.run_session, session.id)

    return service.get_status()


@router.post("/stop", response_model=StopCrawlerResponse)
def stop_crawler(
    service: CrawlerService = Depends(get_crawler_service),
) -> StopCrawlerResponse:
    if not service.is_running:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Crawler is not running.",
        )

    service.request_stop()
    return StopCrawlerResponse(detail="Stop requested.")


@router.get("/status")
def crawler_status(
    service: CrawlerService = Depends(get_crawler_service),
) -> dict:
    return service.get_status()
