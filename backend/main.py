from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from backend.src.api.endpoints import crawler, data, system
from backend.src.database.session import init_db
from backend.src.services.crawler_service import CrawlerService


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    app.state.crawler_service = CrawlerService()
    yield


app = FastAPI(
    title="Reddit Crawler API",
    version="1.0.0",
    description="Backend API for crawling Reddit content into SQLite.",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(crawler.router, prefix="/api/crawler", tags=["crawler"])
app.include_router(data.router, prefix="/api/data", tags=["data"])
app.include_router(system.router, prefix="/api", tags=["system"])


@app.get("/health")
def healthcheck() -> dict[str, str]:
    return {"status": "ok"}
