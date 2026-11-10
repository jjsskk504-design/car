"""Constant-velocity and constant-acceleration prediction baselines."""

from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Mapping, Sequence


@dataclass(frozen=True)
class PredictionConfig:
    horizons_ms: tuple[int, ...] = (1000, 2000, 3000, 4000, 5000)
    max_speed_mps: float = 55.0
    max_accel_mps2: float = 8.0
    stationary_speed_mps: float = 0.20
    braking_mps2: float = 4.0
    lane_width_m: float = 3.5
    lane_change_duration_s: float = 4.0
    road_curvature_per_m: float = 0.0

    def __post_init__(self) -> None:
        if not self.horizons_ms or any(item <= 0 or item > 5000 for item in self.horizons_ms):
            raise ValueError("horizons_ms must contain offsets in the 1-5000 ms simulation window")
        if tuple(sorted(set(self.horizons_ms))) != self.horizons_ms:
            raise ValueError("horizons_ms must be unique and increasing")
        if self.max_speed_mps <= 0 or self.max_accel_mps2 <= 0:
            raise ValueError("speed and acceleration limits must be positive")
        if self.lane_width_m <= 0 or self.lane_change_duration_s <= 0:
            raise ValueError("lane geometry values must be positive")


def _bounded_vector(x: float, y: float, maximum: float) -> tuple[float, float]:
    magnitude = math.hypot(x, y)
    if magnitude <= maximum or magnitude == 0.0:
        return x, y
    scale = maximum / magnitude
    return x * scale, y * scale


def _heading(vx: float, vy: float, fallback: float, stationary_speed: float) -> float:
    if math.hypot(vx, vy) < stationary_speed:
        return fallback
    return math.atan2(vy, vx)


def predict_cv(
    current: Mapping[str, object], config: PredictionConfig = PredictionConfig()
) -> list[dict[str, float | int]]:
    vx, vy = _bounded_vector(float(current["vx"]), float(current["vy"]), config.max_speed_mps)
    heading = _heading(vx, vy, float(current["heading_rad"]), config.stationary_speed_mps)
    points: list[dict[str, float | int]] = []
    for dt_ms in config.horizons_ms:
        seconds = dt_ms / 1000.0
        points.append(
            {
                "dt_ms": dt_ms,
                "x": float(current["x"]) + vx * seconds,
                "y": float(current["y"]) + vy * seconds,
                "heading_rad": heading,
            }
        )
    return points


def estimate_acceleration(
    observations: Sequence[tuple[int, Mapping[str, object]]], config: PredictionConfig
) -> tuple[float, float] | None:
    if len(observations) < 2:
        return None
    previous_timestamp, previous = observations[-2]
    current_timestamp, current = observations[-1]
    dt_seconds = (current_timestamp - previous_timestamp) / 1000.0
    if dt_seconds <= 0:
        return None
    ax = (float(current["vx"]) - float(previous["vx"])) / dt_seconds
    ay = (float(current["vy"]) - float(previous["vy"])) / dt_seconds
    return _bounded_vector(ax, ay, config.max_accel_mps2)


def predict_ca(
    observations: Sequence[tuple[int, Mapping[str, object]]],
    config: PredictionConfig = PredictionConfig(),
) -> list[dict[str, float | int]]:
    if not observations:
        raise ValueError("at least one observation is required")
    current = observations[-1][1]
    acceleration = estimate_acceleration(observations, config)
    if acceleration is None:
        return predict_cv(current, config)
    vx, vy = _bounded_vector(float(current["vx"]), float(current["vy"]), config.max_speed_mps)
    ax, ay = acceleration
    points: list[dict[str, float | int]] = []
    for dt_ms in config.horizons_ms:
        seconds = dt_ms / 1000.0
        dx = vx * seconds + 0.5 * ax * seconds * seconds
        dy = vy * seconds + 0.5 * ay * seconds * seconds
        dx, dy = _bounded_vector(dx, dy, config.max_speed_mps * seconds)
        future_vx, future_vy = _bounded_vector(
            vx + ax * seconds, vy + ay * seconds, config.max_speed_mps
        )
        points.append(
            {
                "dt_ms": dt_ms,
                "x": float(current["x"]) + dx,
                "y": float(current["y"]) + dy,
                "heading_rad": _heading(
                    future_vx,
                    future_vy,
                    float(current["heading_rad"]),
                    config.stationary_speed_mps,
                ),
            }
        )
    return points

