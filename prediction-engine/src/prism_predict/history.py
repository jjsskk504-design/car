"""VehicleFrame loading, validation, ID matching, and history normalization."""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Iterable, Mapping, Sequence

from .coordinates import Pose2D, normalize_angle, transform_vehicle


VEHICLE_FIELDS = (
    "id",
    "x",
    "y",
    "vx",
    "vy",
    "heading_rad",
    "length_m",
    "width_m",
    "lane_id",
)
FINITE_FIELDS = (
    "x",
    "y",
    "vx",
    "vy",
    "heading_rad",
    "length_m",
    "width_m",
)


class VehicleFrameError(ValueError):
    """Raised when a VehicleFrame violates the Part 01 input contract."""


def _finite_number(value: object, label: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise VehicleFrameError(f"{label} must be a number")
    result = float(value)
    if not math.isfinite(result):
        raise VehicleFrameError(f"{label} must be finite")
    return result


def validate_vehicle_frame(frame: Mapping[str, object]) -> None:
    for field in ("timestamp_ms", "ego_id", "vehicles"):
        if field not in frame:
            raise VehicleFrameError(f"missing VehicleFrame field: {field}")
    timestamp = _finite_number(frame["timestamp_ms"], "timestamp_ms")
    if timestamp < 0 or not timestamp.is_integer():
        raise VehicleFrameError("timestamp_ms must be a non-negative integer")
    if not isinstance(frame["ego_id"], str) or not frame["ego_id"]:
        raise VehicleFrameError("ego_id must be a non-empty string")
    vehicles = frame["vehicles"]
    if not isinstance(vehicles, list):
        raise VehicleFrameError("vehicles must be an array")
    seen_ids: set[str] = set()
    for index, raw_vehicle in enumerate(vehicles):
        if not isinstance(raw_vehicle, Mapping):
            raise VehicleFrameError(f"vehicles[{index}] must be an object")
        for field in VEHICLE_FIELDS:
            if field not in raw_vehicle:
                raise VehicleFrameError(f"vehicles[{index}] missing field: {field}")
        vehicle_id = raw_vehicle["id"]
        if not isinstance(vehicle_id, str) or not vehicle_id:
            raise VehicleFrameError(f"vehicles[{index}].id must be a non-empty string")
        if vehicle_id in seen_ids:
            raise VehicleFrameError(f"duplicate vehicle id at one timestamp: {vehicle_id}")
        seen_ids.add(vehicle_id)
        for field in FINITE_FIELDS:
            _finite_number(raw_vehicle[field], f"vehicles[{index}].{field}")
        if float(raw_vehicle["length_m"]) <= 0 or float(raw_vehicle["width_m"]) <= 0:
            raise VehicleFrameError("vehicle dimensions must be positive")
    if frame["ego_id"] not in seen_ids:
        raise VehicleFrameError(f"ego_id {frame['ego_id']!r} is absent from vehicles")


def load_vehicle_frames(path: str | Path) -> list[dict[str, object]]:
    """Load newline-delimited VehicleFrames, validate them, and sort by timestamp."""

    frames: list[dict[str, object]] = []
    with Path(path).open("r", encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, start=1):
            if not line.strip():
                continue
            try:
                frame = json.loads(line)
            except json.JSONDecodeError as error:
                raise VehicleFrameError(f"line {line_number}: invalid JSON: {error}") from error
            if not isinstance(frame, dict):
                raise VehicleFrameError(f"line {line_number}: frame must be an object")
            validate_vehicle_frame(frame)
            frames.append(frame)
    if not frames:
        raise VehicleFrameError("input contains no VehicleFrame")
    frames.sort(key=lambda item: int(item["timestamp_ms"]))
    timestamps = [int(item["timestamp_ms"]) for item in frames]
    if len(timestamps) != len(set(timestamps)):
        raise VehicleFrameError("duplicate timestamp_ms is not allowed")
    _validate_identity_consistency(frames)
    return frames


def _vehicles_by_id(frame: Mapping[str, object]) -> dict[str, dict[str, object]]:
    return {str(vehicle["id"]): vehicle for vehicle in frame["vehicles"]}  # type: ignore[index]


def _validate_identity_consistency(frames: Sequence[Mapping[str, object]]) -> None:
    dimensions: dict[str, tuple[float, float]] = {}
    for frame in frames:
        for vehicle_id, vehicle in _vehicles_by_id(frame).items():
            current = float(vehicle["length_m"]), float(vehicle["width_m"])
            previous = dimensions.setdefault(vehicle_id, current)
            if max(abs(current[0] - previous[0]), abs(current[1] - previous[1])) > 0.50:
                raise VehicleFrameError(
                    f"possible ID reassignment for {vehicle_id}: dimensions changed by over 0.50 m"
                )


def estimate_ego_poses(frames: Sequence[Mapping[str, object]]) -> list[Pose2D]:
    """Dead-reckon frame poses using ego velocity and relative ego heading.

    The common VehicleFrame has no absolute ego pose or yaw rate. This is therefore
    a deterministic estimate. Callers with measured world poses should pass those
    directly to ``normalize_history_to_current_ego``.
    """

    if not frames:
        return []
    first_ego = _vehicles_by_id(frames[0])[str(frames[0]["ego_id"])]
    first_heading = float(first_ego["heading_rad"])
    poses = [Pose2D(0.0, 0.0, 0.0)]
    for previous_frame, frame in zip(frames, frames[1:]):
        dt_seconds = (int(frame["timestamp_ms"]) - int(previous_frame["timestamp_ms"])) / 1000.0
        if dt_seconds <= 0:
            raise VehicleFrameError("timestamps must increase after sorting")
        previous_ego = _vehicles_by_id(previous_frame)[str(previous_frame["ego_id"])]
        current_ego = _vehicles_by_id(frame)[str(frame["ego_id"])]
        previous_pose = poses[-1]
        yaw = normalize_angle(float(current_ego["heading_rad"]) - first_heading)
        cosine = math.cos(previous_pose.yaw_rad)
        sine = math.sin(previous_pose.yaw_rad)
        world_vx = cosine * float(previous_ego["vx"]) - sine * float(previous_ego["vy"])
        world_vy = sine * float(previous_ego["vx"]) + cosine * float(previous_ego["vy"])
        poses.append(
            Pose2D(
                previous_pose.x + world_vx * dt_seconds,
                previous_pose.y + world_vy * dt_seconds,
                yaw,
            )
        )
    return poses


def normalize_history_to_current_ego(
    frames: Sequence[Mapping[str, object]],
    ego_poses: Sequence[Pose2D] | None = None,
) -> list[dict[str, object]]:
    """Transform every historical observation into the newest ego frame."""

    if not frames:
        raise VehicleFrameError("at least one frame is required")
    for frame in frames:
        validate_vehicle_frame(frame)
    poses = list(ego_poses) if ego_poses is not None else estimate_ego_poses(frames)
    if len(poses) != len(frames):
        raise VehicleFrameError("ego_poses count must match VehicleFrame count")
    target_pose = poses[-1]
    normalized: list[dict[str, object]] = []
    for frame, pose in zip(frames, poses):
        normalized.append(
            {
                "timestamp_ms": int(frame["timestamp_ms"]),
                "ego_id": frame["ego_id"],
                "vehicles": [
                    transform_vehicle(vehicle, pose, target_pose)
                    for vehicle in frame["vehicles"]  # type: ignore[index]
                ],
            }
        )
    return normalized


def observations_for_vehicle(
    frames: Iterable[Mapping[str, object]], vehicle_id: str
) -> list[tuple[int, dict[str, object]]]:
    """Match an ID across time; missing frames are skipped, never zero-filled."""

    observations: list[tuple[int, dict[str, object]]] = []
    for frame in frames:
        vehicle = _vehicles_by_id(frame).get(vehicle_id)
        if vehicle is not None:
            observations.append((int(frame["timestamp_ms"]), vehicle))
    return observations

