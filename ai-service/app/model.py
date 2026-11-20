"""
Sales forecasting model (AI component) — pure Python implementation.

Approach
--------
* Demand for a product is split into a LEVEL (average daily sales over the previous 28 days) and a
  multiplicative PATTERN (how a given day differs from that level: weekday, pay-day at month end,
  seasonal/holiday months).
* A pure-Python Random Forest Regressor learns the pattern ratio  y / level  from calendar features.
  Each tree is grown as a random subset of training rows and features; the forest average is used for
  prediction.  The implementation is intentionally direct so it carries no native C extensions.
* Forecast for a future day = current level x predicted ratio. Because the forecast is direct (not fed
  back recursively), errors do not build up over a 30-day horizon.
* AI-01: before forecasting, the model is evaluated on the most recent 14 days (a chronologically
  separated hold-out set) and its Mean Absolute Error is compared with a seasonal-naive baseline
  ("same weekday last week").

No external dependencies beyond the Python standard library — avoids DLL/pyd blocks imposed by
enterprise Application Control policies (WDAC / AppLocker).
"""
from __future__ import annotations

import csv
import math
import random
from dataclasses import dataclass
from datetime import date, timedelta
from functools import lru_cache
from pathlib import Path
from typing import Optional

LEVEL_WINDOW = 28
MIN_HISTORY_DAYS = 42
HOLDOUT_DAYS = 14
MODEL_TYPE = "Random Forest Regressor"
DATASET_PATH = Path(__file__).resolve().parent.parent / "data" / "sales_history.csv"

# Random Forest hyper-parameters (match the original sklearn configuration)
N_ESTIMATORS = 200
MIN_SAMPLES_LEAF = 3
MAX_FEATURES = 3          # roughly sqrt(5 features), same as sklearn default for regressors
RANDOM_SEED = 42


class InsufficientHistoryError(ValueError):
    """Raised when there is not enough data to train and evaluate a model."""


@dataclass
class ForecastResult:
    daily: list[tuple[date, float]]
    total: float
    mae: float
    baseline_mae: float
    accuracy_percent: float
    data_source: str
    history_days: int


# ---------------------------------------------------------------------------
# Lightweight date-indexed series (replaces pd.Series / pd.DataFrame)
# ---------------------------------------------------------------------------

def date_range(start: date, end: date) -> list[date]:
    """Inclusive daily range from start to end."""
    out: list[date] = []
    d = start
    while d <= end:
        out.append(d)
        d += timedelta(days=1)
    return out


def to_series(points: list[tuple[date, float]], end: date | None = None) -> dict[date, float]:
    """
    Daily dict with missing days filled with 0.0, optionally extended up to (and including) `end`.
    Duplicate dates are summed.
    """
    if not points:
        return {}
    raw: dict[date, float] = {}
    for d, q in points:
        raw[d] = raw.get(d, 0.0) + float(q)
    first = min(raw)
    last = max(raw)
    if end is not None and end > last:
        last = end
    return {d: raw.get(d, 0.0) for d in date_range(first, last)}


def calendar_features(d: date) -> list[int]:
    """Return [dow, dom, month, is_payday, is_weekend] for a single date."""
    dow = d.weekday()           # 0=Mon … 6=Sun
    dom = d.day
    month = d.month
    is_payday = 1 if dom >= 25 or dom <= 2 else 0
    is_weekend = 1 if dow >= 5 else 0
    return [dow, dom, month, is_payday, is_weekend]


# ---------------------------------------------------------------------------
# Pure-Python Random Forest
# ---------------------------------------------------------------------------

class _DecisionNode:
    """A single node in a regression decision tree."""
    __slots__ = ("feature", "threshold", "left", "right", "value")

    def __init__(self) -> None:
        self.feature: int = 0
        self.threshold: float = 0.0
        self.left: Optional[_DecisionNode] = None
        self.right: Optional[_DecisionNode] = None
        self.value: float = 0.0   # leaf prediction


def _mean(values: list[float]) -> float:
    return sum(values) / len(values) if values else 0.0


def _mse(values: list[float]) -> float:
    if not values:
        return 0.0
    m = _mean(values)
    return sum((v - m) ** 2 for v in values) / len(values)


def _build_tree(X: list[list[int]], y: list[float], min_leaf: int,
                max_feat: int, rng: random.Random, depth: int = 0) -> _DecisionNode:
    node = _DecisionNode()
    node.value = _mean(y)
    if len(y) < 2 * min_leaf or depth > 20:
        return node

    n_feat = len(X[0])
    feat_idx = rng.sample(range(n_feat), min(max_feat, n_feat))

    best_score = float("inf")
    best_feat = -1
    best_thresh = 0.0
    best_left: list[int] = []
    best_right: list[int] = []

    for fi in feat_idx:
        # Collect unique threshold candidates (midpoints between sorted unique values)
        vals = sorted({X[i][fi] for i in range(len(X))})
        thresholds = [(vals[k] + vals[k + 1]) / 2.0 for k in range(len(vals) - 1)]
        for thresh in thresholds:
            left_idx  = [i for i in range(len(X)) if X[i][fi] <= thresh]
            right_idx = [i for i in range(len(X)) if X[i][fi] >  thresh]
            if len(left_idx) < min_leaf or len(right_idx) < min_leaf:
                continue
            ly = [y[i] for i in left_idx]
            ry = [y[i] for i in right_idx]
            score = (_mse(ly) * len(ly) + _mse(ry) * len(ry)) / len(y)
            if score < best_score:
                best_score = score
                best_feat = fi
                best_thresh = thresh
                best_left = left_idx
                best_right = right_idx

    if best_feat == -1:
        return node  # no split found; leaf

    node.feature = best_feat
    node.threshold = best_thresh
    node.left  = _build_tree([X[i] for i in best_left],  [y[i] for i in best_left],  min_leaf, max_feat, rng, depth + 1)
    node.right = _build_tree([X[i] for i in best_right], [y[i] for i in best_right], min_leaf, max_feat, rng, depth + 1)
    return node


def _predict_one(node: _DecisionNode, x: list[int]) -> float:
    if node.left is None:
        return node.value
    if x[node.feature] <= node.threshold:
        return _predict_one(node.left, x)
    return _predict_one(node.right, x)


@dataclass
class RandomForestRegressor:
    n_estimators: int = N_ESTIMATORS
    min_samples_leaf: int = MIN_SAMPLES_LEAF
    max_features: int = MAX_FEATURES
    random_seed: int = RANDOM_SEED
    _trees: list[_DecisionNode] = None  # type: ignore[assignment]

    def fit(self, X: list[list[int]], y: list[float]) -> "RandomForestRegressor":
        rng = random.Random(self.random_seed)
        n = len(X)
        self._trees = []
        for _ in range(self.n_estimators):
            # Bootstrap sample
            indices = [rng.randint(0, n - 1) for _ in range(n)]
            bX = [X[i] for i in indices]
            by = [y[i] for i in indices]
            tree_rng = random.Random(rng.randint(0, 2**31))
            self._trees.append(_build_tree(bX, by, self.min_samples_leaf, self.max_features, tree_rng))
        return self

    def predict(self, X: list[list[int]]) -> list[float]:
        return [_mean([_predict_one(t, x) for t in self._trees]) for x in X]


# ---------------------------------------------------------------------------
# Training helpers
# ---------------------------------------------------------------------------

@dataclass
class TrainedModel:
    regressor: RandomForestRegressor
    level: float       # average daily sales over the last 28 days of training data
    last_day: date


def _build_training_rows(series: dict[date, float]) -> tuple[list[list[int]], list[float]]:
    """
    For each day in series (after the first 28), compute:
      features = calendar_features(day)
      target   = sales / rolling-28-day-average (ratio)
    Only rows where the rolling average > 0 are included.
    """
    dates = sorted(series)
    X: list[list[int]] = []
    y: list[float] = []
    for i in range(LEVEL_WINDOW, len(dates)):
        window = [series[dates[j]] for j in range(i - LEVEL_WINDOW, i)]
        level = _mean(window)
        if level <= 0:
            continue
        ratio = series[dates[i]] / level
        X.append(calendar_features(dates[i]))
        y.append(ratio)
    return X, y


def train(series: dict[date, float]) -> TrainedModel:
    X, y = _build_training_rows(series)
    if len(X) < 7:
        raise InsufficientHistoryError("Not enough non-zero sales history to train the model")
    rf = RandomForestRegressor(
        n_estimators=N_ESTIMATORS,
        min_samples_leaf=MIN_SAMPLES_LEAF,
        max_features=MAX_FEATURES,
        random_seed=RANDOM_SEED,
    )
    rf.fit(X, y)
    dates = sorted(series)
    level = _mean([series[d] for d in dates[-LEVEL_WINDOW:]])
    return TrainedModel(rf, level, dates[-1])


def predict(model: TrainedModel, start: date, horizon: int) -> list[tuple[date, float]]:
    days = [start + timedelta(days=i) for i in range(horizon)]
    feats = [calendar_features(d) for d in days]
    ratios = model.regressor.predict(feats)
    return [(d, max(0.0, ratio * model.level)) for d, ratio in zip(days, ratios)]


def seasonal_naive(series: dict[date, float], horizon: int) -> list[float]:
    """Baseline: each day equals the same weekday in the last observed 7 days."""
    dates = sorted(series)
    last_week = [series[d] for d in dates[-7:]]
    return [last_week[i % 7] for i in range(horizon)]


def _mae(actual: list[float], predicted: list[float]) -> float:
    if not actual:
        return 0.0
    return sum(abs(a - p) for a, p in zip(actual, predicted)) / len(actual)


def evaluate(series: dict[date, float], holdout: int = HOLDOUT_DAYS) -> tuple[float, float, float]:
    """
    Hold-out evaluation on the last `holdout` days.
    Returns (model_mae, baseline_mae, accuracy_percent).
    """
    dates = sorted(series)
    train_dates = dates[:-holdout]
    test_dates  = dates[-holdout:]
    train_series = {d: series[d] for d in train_dates}
    test_actual  = [series[d] for d in test_dates]

    trained = train(train_series)
    pred_pairs = predict(trained, test_dates[0], holdout)
    predicted_vals = [v for _, v in pred_pairs]

    baseline_vals = seasonal_naive(train_series, holdout)

    mae_val      = _mae(test_actual, predicted_vals)
    baseline_mae = _mae(test_actual, baseline_vals)

    total = sum(test_actual)
    if total > 0:
        wape = sum(abs(a - p) for a, p in zip(test_actual, predicted_vals)) / total
    else:
        wape = 1.0
    accuracy = max(0.0, 100.0 * (1.0 - wape))
    return float(mae_val), float(baseline_mae), float(accuracy)


# ---------------------------------------------------------------------------
# Reference dataset (cold start)
# ---------------------------------------------------------------------------

@lru_cache(maxsize=1)
def _load_csv() -> list[dict]:
    """Load sales_history.csv into a list of row dicts. Cached after first read."""
    if not DATASET_PATH.exists():
        return []
    rows: list[dict] = []
    with open(DATASET_PATH, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append(row)
    return rows


def reference_series(product_name: str | None, end: date) -> dict[date, float] | None:
    """
    Last 180 days of the reference dataset for this product, re-dated so it ends on `end`.
    The index is shifted by a whole number of weeks so day-of-week patterns are preserved.
    """
    if not product_name:
        return None
    key = product_name.strip().lower()
    rows = _load_csv()
    matching = [r for r in rows if r.get("product", "").strip().lower() == key]
    if not matching:
        return None

    # Parse and sort
    parsed: list[tuple[date, float]] = []
    for r in matching:
        try:
            d = date.fromisoformat(r["date"].strip())
            q = float(r["quantity_sold"])
            parsed.append((d, q))
        except (KeyError, ValueError):
            continue
    if not parsed:
        return None

    parsed.sort()
    # Take last 180 days worth of rows
    parsed = parsed[-180:]
    last_ref_day = parsed[-1][0]

    # Shift by whole weeks so weekday patterns align with the target end date
    offset_days = (end - last_ref_day).days
    offset_days -= offset_days % 7

    shifted: list[tuple[date, float]] = [
        (d + timedelta(days=offset_days), q) for d, q in parsed
    ]
    return to_series(shifted)


# ---------------------------------------------------------------------------
# Public entry point
# ---------------------------------------------------------------------------

def forecast(history: list[tuple[date, float]], horizon: int, start: date,
             product_name: str | None = None) -> ForecastResult:
    """Forecast daily sales for `horizon` days starting on `start`."""
    yesterday = start - timedelta(days=1)
    usable = [(d, q) for d, q in history if d <= yesterday]
    series = to_series(usable, end=yesterday)
    source = "Market sales history"

    if len(series) < MIN_HISTORY_DAYS:
        ref = reference_series(product_name, yesterday)
        if ref is None:
            raise InsufficientHistoryError(
                f"At least {MIN_HISTORY_DAYS} days of sales history are needed to forecast this product "
                f"(only {len(series)} available).")
        series = ref
        source = "Reference dataset (cold start - not enough market history yet)"

    mae, baseline_mae, accuracy = evaluate(series)
    trained = train(series)
    daily_pairs = predict(trained, start, horizon)
    daily = [(d, round(v, 2)) for d, v in daily_pairs]
    return ForecastResult(
        daily=daily,
        total=round(sum(v for _, v in daily), 2),
        mae=round(mae, 2),
        baseline_mae=round(baseline_mae, 2),
        accuracy_percent=round(accuracy, 1),
        data_source=source,
        history_days=len(series),
    )
