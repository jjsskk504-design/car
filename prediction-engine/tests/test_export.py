import math

from prism_predict.baseline import PredictionConfig
from prism_predict.export import build_prediction_frame, validate_prediction_frame


def _vehicle(vehicle_id: str, y: float) -> dict[str, object]:
    return {
        "id": vehicle_id,
        "x": 0.0,
        "y": y,
        "vx": 0.0,
        "vy": 2.0,
        "heading_rad": math.pi / 2,
        "length_m": 4.5,
        "width_m": 1.8,
        "lane_id": "L1",
    }


def test_prediction_frame_has_four_modes_and_null_confidence() -> None:
    frames = [
        {"timestamp_ms": 0, "ego_id": "ego", "vehicles": [_vehicle("ego", 0), _vehicle("car", 10)]},
        {"timestamp_ms": 1000, "ego_id": "ego", "vehicles": [_vehicle("ego", 0), _vehicle("car", 12)]},
    ]
    result = build_prediction_frame(frames, PredictionConfig(horizons_ms=(1000, 2000)))
    trajectories = result["predictions"][0]["trajectories"]
    assert [item["mode"] for item in trajectories] == [
        "lane_keep",
        "braking",
        "lane_change_left",
        "lane_change_right",
    ]
    assert all(item["confidence"] is None for item in trajectories)
    validate_prediction_frame(result, {"ego", "car"})

