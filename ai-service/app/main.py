"""
AI Forecasting Service - Python + FastAPI.

Endpoint used by the Spring Boot application (Assignment 3, section 4.3):

    POST /api/forecast
    Header: X-API-Key: <shared secret>
    Body:   {"productId": 101, "forecastPeriodDays": 7, "productName": "Tomatoes",
             "startDate": "2026-09-16", "history": [{"date": "2026-06-01", "quantity": 58}, ...]}
    Reply:  {"productId": 101, "predictedQuantity": 420, "dailyForecast": [...], ...}

Run:  uvicorn app.main:app --port 8000
"""
from __future__ import annotations

import os
import time
from datetime import date, datetime

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from . import model

API_KEY = os.getenv("AI_API_KEY", "fresh-produce-shared-key")

app = FastAPI(
    title="Fresh Produce AI Forecasting Service",
    version="1.0.0",
    description="Machine-learning sales forecasts for the Fresh Produce Market POS system.",
)


# ---------------------------------------------------------------------------
# Schemas (AI-03 input validation is declared here)
# ---------------------------------------------------------------------------
class HistoryPoint(BaseModel):
    date: date
    quantity: float = Field(ge=0, description="Quantity sold on that day")


class ForecastRequest(BaseModel):
    productId: int = Field(gt=0, description="Product identifier from the POS database")
    forecastPeriodDays: int = Field(gt=0, le=30, description="Number of days to forecast (1-30)")
    productName: str | None = Field(default=None, max_length=100)
    startDate: date | None = Field(default=None, description="First forecast day; defaults to today")
    history: list[HistoryPoint] = Field(default_factory=list, max_length=1000)


class DailyPoint(BaseModel):
    date: date
    quantity: float


class ForecastResponse(BaseModel):
    productId: int
    predictedQuantity: float
    forecastPeriodDays: int
    dailyForecast: list[DailyPoint]
    modelType: str
    mae: float
    baselineMae: float
    accuracyPercent: float
    dataSource: str
    historyDays: int
    processingTimeMs: int
    generatedAt: datetime


# ---------------------------------------------------------------------------
# Security and error handling
# ---------------------------------------------------------------------------
def verify_api_key(x_api_key: str | None = Header(default=None)) -> None:
    """Only the Spring Boot application (which holds the shared key) may request forecasts."""
    if x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="Missing or invalid API key")


@app.exception_handler(RequestValidationError)
async def validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
    errors = jsonable_encoder(exc.errors(), custom_encoder={Exception: str})
    first = errors[0] if errors else {}
    field = ".".join(str(p) for p in first.get("loc", []) if p != "body")
    message = f"{field}: {first.get('msg', 'invalid value')}" if field else first.get("msg", "Invalid request")
    return JSONResponse(status_code=422, content={"error": "Validation error", "message": message, "detail": errors})


@app.exception_handler(HTTPException)
async def http_error(_: Request, exc: HTTPException) -> JSONResponse:
    return JSONResponse(status_code=exc.status_code, content={"error": "Request rejected", "message": exc.detail})


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------
@app.get("/health")
def health() -> dict:
    return {"status": "UP", "model": model.MODEL_TYPE, "referenceDataset": model.DATASET_PATH.exists()}


@app.post("/api/forecast", response_model=ForecastResponse, dependencies=[Depends(verify_api_key)])
def create_forecast(request: ForecastRequest) -> ForecastResponse:
    started = time.perf_counter()
    start = request.startDate or date.today()
    if any(p.date >= start for p in request.history):
        raise HTTPException(status_code=422, detail="History may only contain days before the forecast start date")

    try:
        result = model.forecast(
            history=[(p.date, p.quantity) for p in request.history],
            horizon=request.forecastPeriodDays,
            start=start,
            product_name=request.productName,
        )
    except model.InsufficientHistoryError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    return ForecastResponse(
        productId=request.productId,
        predictedQuantity=result.total,
        forecastPeriodDays=request.forecastPeriodDays,
        dailyForecast=[DailyPoint(date=d, quantity=q) for d, q in result.daily],
        modelType=model.MODEL_TYPE,
        mae=result.mae,
        baselineMae=result.baseline_mae,
        accuracyPercent=result.accuracy_percent,
        dataSource=result.data_source,
        historyDays=result.history_days,
        processingTimeMs=int((time.perf_counter() - started) * 1000),
        generatedAt=datetime.now(),
    )
