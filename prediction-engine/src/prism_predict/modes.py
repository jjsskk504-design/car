"""Deterministic multi-modal trajectory candidates for Part 01."""

from __future__ import annotations

import math
from typing import Mapping, Sequence

from .baseline import PredictionConfig, predict_ca


def _smoothstep(progress: float) -> float:
    value = min(1.0, max(0.0, progress))
    return value * value * (3.0 - 2.0 * value)


def _lane_keep_with_curvature(
    base: Sequence[Mapping[str, float | int]],
    current: Mapping[str, object],
    config: PredictionConfig,
) -> list[dict[str, float | int]]:
    if abs(config.road_curvature_per_m) < 1e-12:
        return [dict(point) for point in base]
    origin_x, origin_y = float(current["x"]), float(current["y"])
    speed = math.hypot(float(current["vx"]), float(current["vy"]))
    if speed < config.stationary_speed_mps:
        initial_heading = float(current["heading_rad"])
    else:
        initial_heading = math.atan2(float(current["vy"]), float(current["vx"]))
    curvature = config.road_curvature_per_m
    result: list[dict[str, float | int]] = []
    for point in base:
        dx, dy = float(point["x"]) - origin_x, float(point["y"]) - origin_y
        distance = math.hypot(dx, dy)
        end_heading = initial_heading + curvature * distance
        curved_x = origin_x + (math.sin(end_heading) - math.sin(initial_heading)) / curvature
        curved_y = origin_y - (math.cos(end_heading) - math.cos(initial_heading)) / curvature
        result.append({**point, "x": curved_x, "y": curved_y, "heading_rad": end_heading})
    return result


def _braking(
    current: Mapping[str, object], config: PredictionConfig
) -> list[dict[str, float | int]]:
    vx, vy = float(current["vx"]), float(current["vy"])
    speed = min(math.hypot(vx, vy), config.max_speed_mps)
    if speed < config.stationary_speed_mps:
        direction_x = direction_y = 0.0
    else:
        direction_x, direction_y = vx / math.hypot(vx, vy), vy / math.hypot(vx, vy)
    points: list[dict[str, float | int]] = []
    stop_time = speed / config.braking_mps2 if config.braking_mps2 > 0 else 0.0
    for dt_ms in config.horizons_ms:
        seconds = dt_ms / 1000.0
        moving_time = min(seconds, stop_time)
        distance = speed * moving_time - 0.5 * config.braking_mps2 * moving_time**2
        points.append(
            {
                "dt_ms": dt_ms,
                "x": float(current["x"]) + direction_x * distance,
                "y": float(current["y"]) + direction_y * distance,
                "heading_rad": float(current["heading_rad"]),
            }
        )
    return points


def _lane_change(
    lane_keep: Sequence[Mapping[str, float | int]],
    current: Mapping[str, object],
    config: PredictionConfig,
    direction: int,
) -> list[dict[str, float | int]]:
    vx, vy = float(current["vx"]), float(current["vy"])
    speed = math.hypot(vx, vy)
    if speed < config.stationary_speed_mps:
        heading = float(current["heading_rad"])
        unit_x, unit_y = math.cos(heading), math.sin(heading)
    else:
        unit_x, unit_y = vx / speed, vy / speed
    left_x, left_y = -unit_y, unit_x
    points: list[dict[str, float | int]] = []
    for point in lane_keep:
        progress = (int(point["dt_ms"]) / 1000.0) / config.lane_change_duration_s
        offset = direction * config.lane_width_m * _smoothstep(progress)
        points.append(
            {
                **point,
                "x": float(point["x"]) + left_x * offset,
                "y": float(point["y"]) + left_y * offset,
            }
        )
    return points


def candidate_trajectories(
    observations: Sequence[tuple[int, Mapping[str, object]]],
    config: PredictionConfig = PredictionConfig(),
) -> list[dict[str, object]]:
    if not observations:
        raise ValueError("at least one observation is required")
    current = observations[-1][1]
    lane_keep = _lane_keep_with_curvature(predict_ca(observations, config), current, config)
    return [
        {"mode": "lane_keep", "confidence": None, "points": lane_keep},
        {"mode": "braking", "confidence": None, "points": _braking(current, config)},
        {
            "mode": "lane_change_left",
            "confidence": None,
            "points": _lane_change(lane_keep, current, config, 1),
        },
        {
            "mode": "lane_change_right",
            "confidence": None,
            "points": _lane_change(lane_keep, current, config, -1),
        },
    ]

