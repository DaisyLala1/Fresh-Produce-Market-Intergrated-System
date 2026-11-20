"""Automated tests for the AI forecasting service (AI-01, AI-02, AI-03). Run: pytest -q"""
import time
from datetime import date, timedelta

import numpy as np
from fastapi.testclient import TestClient

from app.main import API_KEY, app

client = TestClient(app)
HEADERS = {"X-API-Key": API_KEY}
TODAY = date(2026, 9, 16)


def history(days=120, base=60.0, seed=1):
    rng = np.random.default_rng(seed)
    weekday = [0.85, 0.9, 0.95, 1.0, 1.2, 1.35, 0.75]
    out = []
    for i in range(days, 0, -1):
        d = TODAY - timedelta(days=i)
        out.append({"date": d.isoformat(), "quantity": round(max(0, rng.normal(base * weekday[d.weekday()], 5)))})
    return out


def body(**overrides):
    b = {"productId": 101, "productName": "Tomatoes", "forecastPeriodDays": 7,
         "startDate": TODAY.isoformat(), "history": history()}
    b.update(overrides)
    return b


def test_health():
    assert client.get("/health").json()["status"] == "UP"


def test_tc05_valid_request_returns_forecast():
    r = client.post("/api/forecast", json=body(), headers=HEADERS)
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["productId"] == 101
    assert len(data["dailyForecast"]) == 7
    assert data["dailyForecast"][0]["date"] == TODAY.isoformat()
    assert abs(data["predictedQuantity"] - sum(p["quantity"] for p in data["dailyForecast"])) < 0.1
    assert data["predictedQuantity"] > 0


def test_ai01_model_beats_seasonal_naive_baseline():
    data = client.post("/api/forecast", json=body(), headers=HEADERS).json()
    assert data["mae"] < data["baselineMae"]


def test_ai02_response_within_five_seconds():
    started = time.perf_counter()
    r = client.post("/api/forecast", json=body(forecastPeriodDays=30), headers=HEADERS)
    assert r.status_code == 200
    assert time.perf_counter() - started < 5


def test_ai03_zero_period_rejected():
    r = client.post("/api/forecast", json=body(forecastPeriodDays=0), headers=HEADERS)
    assert r.status_code == 422
    assert "forecastPeriodDays" in r.json()["message"]


def test_ai03_invalid_product_id_rejected():
    assert client.post("/api/forecast", json=body(productId=-5), headers=HEADERS).status_code == 422


def test_ai03_missing_field_rejected():
    b = body()
    del b["productId"]
    assert client.post("/api/forecast", json=b, headers=HEADERS).status_code == 422


def test_ai03_negative_quantity_rejected():
    b = body(history=[{"date": "2026-09-01", "quantity": -3}])
    assert client.post("/api/forecast", json=b, headers=HEADERS).status_code == 422


def test_missing_api_key_rejected():
    assert client.post("/api/forecast", json=body()).status_code == 401


def test_cold_start_uses_reference_dataset():
    r = client.post("/api/forecast", json=body(history=history(days=10)), headers=HEADERS)
    assert r.status_code == 200
    assert "Reference dataset" in r.json()["dataSource"]


def test_unknown_product_without_history_rejected():
    r = client.post("/api/forecast", json=body(productName="Dragon Fruit", history=[]), headers=HEADERS)
    assert r.status_code == 422
    assert "history" in r.json()["message"]
