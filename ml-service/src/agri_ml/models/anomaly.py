"""Generic robust anomaly check for the last point of a series (no trained model).

The last value is compared with the median of the previous `window` values. The spread is the
median absolute deviation (MAD); robust z = 0.6745 * (x - median) / MAD (Iglewicz & Hoaglin).
If MAD is 0, the IQR / 1.349 is used; if that is also 0, the history is flat: z is null and any
change from it is reported as an anomaly. Fewer than MIN_POINTS prior values is INSUFFICIENT_DATA.
"""

from __future__ import annotations

import numpy as np

METHOD_VERSION = "anomaly-robust-z-v1"
MIN_POINTS = 5


def evaluate(values: list[float], window: int, threshold: float) -> dict:
    x = float(values[-1])
    prior = np.asarray(values[:-1][-window:], dtype=float)
    base = {"observed_value": x, "window_used": int(len(prior)), "threshold": threshold}
    if len(prior) < MIN_POINTS:
        return {**base, "status": "INSUFFICIENT_DATA", "direction": "NONE", "baseline": None,
                "deviation": None, "deviation_pct": None, "robust_z": None,
                "method": f"robust z-score needs at least {MIN_POINTS} prior points"}
    med = float(np.median(prior))
    mad = float(np.median(np.abs(prior - med)))
    if mad > 0:
        z = 0.6745 * (x - med) / mad
        spread = "MAD"
    else:
        q1, q3 = np.percentile(prior, [25, 75])
        iqr_sigma = (q3 - q1) / 1.349
        z = (x - med) / iqr_sigma if iqr_sigma > 0 else None
        spread = "IQR/1.349 (MAD was 0)"
    dev = x - med
    anomalous = z is not None and abs(z) > threshold
    if z is None and dev != 0:
        anomalous = True  # flat history, any change stands out
    return {
        **base,
        "status": "ANOMALY" if anomalous else "NORMAL",
        "direction": ("HIGH" if dev > 0 else "LOW") if anomalous else "NONE",
        "baseline": med,
        "deviation": dev,
        "deviation_pct": 100 * dev / med if med else None,
        "robust_z": None if z is None else round(float(z), 3),
        "method": f"robust z-score: last value vs. median of previous {len(prior)} values, "
                  f"spread = {spread}; anomaly if |z| > {threshold:g}",
    }
