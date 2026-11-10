import math

import pytest

from prism_predict.baseline import PredictionConfig, predict_ca
from prism_predict.history import observations_for_vehicle


def test_missing_observation_is_skipped_not_zero_filled() -> None:
    frames = [
        {
            "timestamp_ms": 0,
            "vehicles": [{"id": "car_01", "x": 1.0, "y": 2.0, "vx": 0.0, "vy": 1.0, "heading_rad": math.pi / 2}],
        },
        {"timestamp_ms": 1000, "vehicles": []},
        {
            "timestamp_ms": 2000,
            "vehicles": [{"id": "car_01", "x": 1.0, "y": 4.0, "vx": 0.0, "vy": 1.0, "heading_rad": math.pi / 2}],
        },
    ]
    observations = observations_for_vehicle(frames, "car_01")
    assert [timestamp for timestamp, _ in observations] == [0, 2000]


def test_ca_falls_back_to_cv_with_one_observation() -> None:
    observations = [
        (2000, {"x": 1.0, "y": 4.0, "vx": 0.0, "vy": 2.0, "heading_rad": math.pi / 2})
    ]
    point = predict_ca(observations, PredictionConfig(horizons_ms=(1000,)))[0]
    assert point["x"] == pytest.approx(1.0)
    assert point["y"] == pytest.approx(6.0)

