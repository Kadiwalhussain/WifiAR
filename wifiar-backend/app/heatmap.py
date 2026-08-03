"""Server-side heatmap via Gaussian Process regression (smoother than on-device IDW)."""

from __future__ import annotations

import math
import time
from typing import Sequence

import numpy as np
from sklearn.gaussian_process import GaussianProcessRegressor
from sklearn.gaussian_process.kernels import ConstantKernel, Matern, WhiteKernel

from app.schemas import HeatmapGridOut


def recompute_heatmap_gp(
    points: Sequence[tuple[float, float, float]],
    cell_size: float = 0.3,
    padding: float = 0.5,
    max_axis: int = 64,
) -> HeatmapGridOut:
    """
    Interpolate RSSI over the horizontal (x, z) plane using a Gaussian Process.

    points: sequence of (x, z, rssi_dbm)
    """
    t0 = time.perf_counter()
    if not points:
        return HeatmapGridOut(
            method="gaussian_process",
            min_x=0.0,
            max_x=0.0,
            min_z=0.0,
            max_z=0.0,
            cell_size=cell_size,
            cols=0,
            rows=0,
            values=[],
            sample_count=0,
            compute_ms=0,
        )

    arr = np.asarray(points, dtype=np.float64)
    xs, zs, ys = arr[:, 0], arr[:, 1], arr[:, 2]

    # Deduplicate near-identical (x,z) by averaging RSSI — helps GP conditioning.
    rounded = np.round(np.column_stack([xs, zs]), 3)
    unique, inv = np.unique(rounded, axis=0, return_inverse=True)
    agg = np.zeros(len(unique), dtype=np.float64)
    counts = np.zeros(len(unique), dtype=np.int32)
    for i, g in enumerate(inv):
        agg[g] += ys[i]
        counts[g] += 1
    agg /= counts

    X = unique
    y = agg

    min_x = float(X[:, 0].min() - padding)
    max_x = float(X[:, 0].max() + padding)
    min_z = float(X[:, 1].min() - padding)
    max_z = float(X[:, 1].max() + padding)

    min_span = cell_size * 2
    if max_x - min_x < min_span:
        mid = (min_x + max_x) / 2
        min_x, max_x = mid - min_span / 2, mid + min_span / 2
    if max_z - min_z < min_span:
        mid = (min_z + max_z) / 2
        min_z, max_z = mid - min_span / 2, mid + min_span / 2

    cols = min(max_axis, max(1, int((max_x - min_x) / cell_size) + 1))
    rows = min(max_axis, max(1, int((max_z - min_z) / cell_size) + 1))
    step_x = (max_x - min_x) / cols
    step_z = (max_z - min_z) / rows
    effective_cell = max(step_x, step_z, 1e-4)

    # Kernel: smooth Matérn + noise; length scale in metres.
    kernel = ConstantKernel(1.0, (1e-2, 1e3)) * Matern(
        length_scale=1.5, length_scale_bounds=(0.2, 20.0), nu=1.5
    ) + WhiteKernel(noise_level=1.0, noise_level_bounds=(1e-3, 50.0))

    # With a single unique point, GP can't fit — fill constant grid.
    if len(X) < 2:
        fill = float(y[0])
        values = [fill] * (cols * rows)
    else:
        gpr = GaussianProcessRegressor(
            kernel=kernel,
            normalize_y=True,
            n_restarts_optimizer=1,
            alpha=1e-6,
        )
        gpr.fit(X, y)

        grid_x = min_x + (np.arange(cols) + 0.5) * step_x
        grid_z = min_z + (np.arange(rows) + 0.5) * step_z
        gx, gz = np.meshgrid(grid_x, grid_z)
        query = np.column_stack([gx.ravel(), gz.ravel()])
        pred = gpr.predict(query)
        values = [float(v) if math.isfinite(float(v)) else float("nan") for v in pred]

    # JSON-friendly: replace NaN with null via None
    json_values = [None if (isinstance(v, float) and math.isnan(v)) else v for v in values]

    elapsed_ms = int((time.perf_counter() - t0) * 1000)
    return HeatmapGridOut(
        method="gaussian_process",
        min_x=min_x,
        max_x=max_x,
        min_z=min_z,
        max_z=max_z,
        cell_size=effective_cell,
        cols=cols,
        rows=rows,
        values=json_values,  # type: ignore[arg-type]
        sample_count=len(points),
        compute_ms=elapsed_ms,
    )
