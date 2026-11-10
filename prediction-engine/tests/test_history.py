import math
import json

import pytest

from prism_predict.coordinates import Pose2D
from prism_predict.history import (
    VehicleFrameError,
    load_vehicle_frames,
    normalize_history_to_current_ego,
    observations_for_vehicle,
    validate_vehicle_frame,
)


def vehicle(vehicle_id: str, x: float, y: float, length: float = 4.5) -> dict[str, object]:
    return {
        "id": vehicle_id,
        "x": x,
        "y": y,
        "vx": 0.0,
        "vy": 0.0,
        "heading_rad": math.pi / 2,
        "length_m": length,
        "width_m": 1.8,
        "lane_id": "L1",
    }


def frame(timestamp: int, *vehicles: dict[str, object]) -> dict[str, object]:
    return {"timestamp_ms": timestamp, "ego_id": "ego", "vehicles": list(vehicles)}


def test_explicit_ego_poses_normalize_history_to_latest_frame() -> None:
    frames = [frame(0, vehicle("ego", 0, 0), vehicle("car", 2, 12)), frame(1000, vehicle("ego", 0, 0), vehicle("car", 2, 4))]
    normalized = normalize_history_to_current_ego(
        frames, [Pose2D(0, 0, 0), Pose2D(0, 10, 0)]
    )
    old_car = observations_for_vehicle(normalized, "car")[0][1]
    assert (old_car["x"], old_car["y"]) == pytest.approx((2.0, 2.0))


def test_missing_ego_is_rejected() -> None:
    with pytest.raises(VehicleFrameError, match="ego_id"):
        validate_vehicle_frame(frame(0, vehicle("other", 0, 0)))


def test_loader_sorts_regressive_input_order(tmp_path) -> None:
    path = tmp_path / "unordered.jsonl"
    values = [frame(1000, vehicle("ego", 0, 0)), frame(0, vehicle("ego", 0, 0))]
    path.write_text("\n".join(json.dumps(item) for item in values), encoding="utf-8")
    loaded = load_vehicle_frames(path)
    assert [item["timestamp_ms"] for item in loaded] == [0, 1000]


def test_loader_rejects_duplicate_timestamp(tmp_path) -> None:
    path = tmp_path / "duplicate.jsonl"
    values = [frame(0, vehicle("ego", 0, 0)), frame(0, vehicle("ego", 0, 0))]
    path.write_text("\n".join(json.dumps(item) for item in values), encoding="utf-8")
    with pytest.raises(VehicleFrameError, match="duplicate timestamp"):
        load_vehicle_frames(path)


def test_loader_rejects_possible_id_reassignment(tmp_path) -> None:
    changed = vehicle("car", 0, 5)
    changed["length_m"] = 7.0
    values = [
        frame(0, vehicle("ego", 0, 0), vehicle("car", 0, 5)),
        frame(1000, vehicle("ego", 0, 0), changed),
    ]
    path = tmp_path / "reassigned.jsonl"
    path.write_text("\n".join(json.dumps(item) for item in values), encoding="utf-8")
    with pytest.raises(VehicleFrameError, match="ID reassignment"):
        load_vehicle_frames(path)

