"""Coordinate conversion helpers for the PRISM x-right/y-forward convention."""

from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Mapping


@dataclass(frozen=True)
class Pose2D:
    """A local frame origin and orientation expressed in a common world frame."""

    x: float
    y: float
    yaw_rad: float


def normalize_angle(angle_rad: float) -> float:
    """Return an angle in [-pi, pi)."""

    return (angle_rad + math.pi) % (2.0 * math.pi) - math.pi


def _rotate(x: float, y: float, angle_rad: float) -> tuple[float, float]:
    cosine = math.cos(angle_rad)
    sine = math.sin(angle_rad)
    return cosine * x - sine * y, sine * x + cosine * y


def local_to_world_point(x: float, y: float, pose: Pose2D) -> tuple[float, float]:
    rotated_x, rotated_y = _rotate(x, y, pose.yaw_rad)
    return rotated_x + pose.x, rotated_y + pose.y


def world_to_local_point(x: float, y: float, pose: Pose2D) -> tuple[float, float]:
    return _rotate(x - pose.x, y - pose.y, -pose.yaw_rad)


def transform_point(
    x: float,
    y: float,
    source_pose: Pose2D,
    target_pose: Pose2D,
) -> tuple[float, float]:
    """Transform a point from a source local frame to a target local frame."""

    world_x, world_y = local_to_world_point(x, y, source_pose)
    return world_to_local_point(world_x, world_y, target_pose)


def transform_vector(
    x: float,
    y: float,
    source_pose: Pose2D,
    target_pose: Pose2D,
) -> tuple[float, float]:
    world_x, world_y = _rotate(x, y, source_pose.yaw_rad)
    return _rotate(world_x, world_y, -target_pose.yaw_rad)


def transform_heading(
    heading_rad: float,
    source_pose: Pose2D,
    target_pose: Pose2D,
) -> float:
    return normalize_angle(heading_rad + source_pose.yaw_rad - target_pose.yaw_rad)


def transform_vehicle(
    vehicle: Mapping[str, object],
    source_pose: Pose2D,
    target_pose: Pose2D,
) -> dict[str, object]:
    """Return a VehicleFrame vehicle transformed without changing its metadata."""

    transformed = dict(vehicle)
    transformed["x"], transformed["y"] = transform_point(
        float(vehicle["x"]), float(vehicle["y"]), source_pose, target_pose
    )
    transformed["vx"], transformed["vy"] = transform_vector(
        float(vehicle["vx"]), float(vehicle["vy"]), source_pose, target_pose
    )
    transformed["heading_rad"] = transform_heading(
        float(vehicle["heading_rad"]), source_pose, target_pose
    )
    return transformed

