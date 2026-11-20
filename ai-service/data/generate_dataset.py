"""
Generates sales_history.csv - a two-year daily fresh-produce sales dataset in the same layout as the
Kaggle agricultural sales data used to develop the model (date, product, category, quantity_sold, unit_price).

The dataset is used for:
  * offline model development and evaluation (evaluate.py), and
  * a "cold start" when the market has fewer than 35 days of its own sales history for a product.

Run:  python data/generate_dataset.py
"""
from pathlib import Path

import numpy as np
import pandas as pd

PRODUCTS = [
    # name, category, base daily demand (kg / bunches), price, summer factor
    ("Tomatoes", "Vegetables", 60, 15.00, 1.15),
    ("Potatoes", "Vegetables", 80, 10.00, 0.95),
    ("Onions", "Vegetables", 50, 12.00, 1.00),
    ("Carrots", "Vegetables", 35, 10.00, 1.00),
    ("Green Peppers", "Vegetables", 20, 18.00, 1.10),
    ("Cabbage", "Vegetables", 30, 8.00, 0.85),
    ("Spinach", "Vegetables", 25, 14.00, 0.90),
    ("Bananas", "Fruit", 45, 12.00, 1.05),
    ("Apples", "Fruit", 40, 16.00, 0.90),
    ("Oranges", "Fruit", 35, 14.00, 0.80),
    ("Avocados", "Fruit", 15, 22.00, 1.10),
    ("Plantains", "Fruit", 10, 11.00, 1.00),
]
WEEKDAY = np.array([0.85, 0.90, 0.95, 1.00, 1.20, 1.35, 0.75])  # Monday .. Sunday


def generate(start="2024-01-01", days=730, seed=7) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    dates = pd.date_range(start, periods=days, freq="D")
    rows = []
    for name, category, base, price, summer in PRODUCTS:
        for i, d in enumerate(dates):
            # Southern-hemisphere seasonality: summer peaks around January.
            season = 1 + (summer - 1) * np.cos(2 * np.pi * (d.dayofyear - 15) / 365.25)
            month_end = 1.12 if d.day >= 25 or d.day <= 2 else 1.0   # pay-day effect
            holiday = 1.3 if (d.month == 12 and 15 <= d.day <= 24) or (d.month == 4 and 1 <= d.day <= 5) else 1.0
            trend = 1 + 0.0004 * i
            mean = base * WEEKDAY[d.dayofweek] * season * month_end * holiday * trend
            qty = max(0, int(round(rng.normal(mean, mean * 0.12))))
            rows.append((d.date().isoformat(), name, category, qty, price))
    return pd.DataFrame(rows, columns=["date", "product", "category", "quantity_sold", "unit_price"])


if __name__ == "__main__":
    out = Path(__file__).with_name("sales_history.csv")
    df = generate()
    df.to_csv(out, index=False)
    print(f"Wrote {len(df):,} rows for {df['product'].nunique()} products to {out}")
