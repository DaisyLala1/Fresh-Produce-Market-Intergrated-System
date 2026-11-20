"""
AI-01 evidence: evaluates the Random Forest model against the seasonal-naive baseline for every product
in the reference dataset, using the last 28 days as a chronologically separated test set.

Run:  python evaluate.py
"""
import pandas as pd

from app import model


def main(holdout: int = 28) -> None:
    df = model.reference_dataset()
    rows = []
    for product, group in df.groupby("product"):
        series = group.set_index("date")["quantity_sold"].astype(float).sort_index()
        mae, baseline_mae, accuracy = model.evaluate(series, holdout=holdout)
        rows.append({"product": product, "model_mae": round(mae, 2), "baseline_mae": round(baseline_mae, 2),
                     "improvement_%": round(100 * (baseline_mae - mae) / baseline_mae, 1),
                     "accuracy_%": round(accuracy, 1)})
    table = pd.DataFrame(rows).sort_values("product")
    print(table.to_string(index=False))
    print()
    print(f"Average model MAE:    {table['model_mae'].mean():.2f}")
    print(f"Average baseline MAE: {table['baseline_mae'].mean():.2f}")
    better = (table["model_mae"] < table["baseline_mae"]).sum()
    print(f"Model beats baseline for {better} of {len(table)} products")
    print("AI-01 target met" if table["model_mae"].mean() < table["baseline_mae"].mean() else "AI-01 target NOT met")


if __name__ == "__main__":
    main()
