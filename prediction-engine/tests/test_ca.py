import math

import pytest

from prism_predict.baseline import PredictionConfig, predict_ca


def test_ca_uses_velocity_difference_over_time() -> None:
    observations = [
        (0, {"x": 0.0, "y": 0.0, "vx": 0.0, "vy": 2.0, "heading_rad": math.pi / 2}),
        (1000, {"x": 0.0, "y": 3.0, "vx": 0.0, "vy": 4.0, "heading_rad": math.pi / 2}),
    ]
    point = predict_ca(observations, PredictionConfig(horizons_ms=(1000,)))[0]
    assert point["x"] == pytest.approx(0.0)
    assert point["y"] == pytest.approx(8.0)
    assert point["heading_rad"] == pytest.approx(math.pi / 2)


def test_ca_clamps_unreasonable_acceleration() -> None:
    observations = [
        (0, {"x": 0.0, "y": 0.0, "vx": 0.0, "vy": 0.0, "heading_rad": 0.0}),
        (100, {"x": 0.0, "y": 0.0, "vx": 100.0, "vy": 0.0, "heading_rad": 0.0}),
    ]
    config = PredictionConfig(horizons_ms=(1000,), max_speed_mps=20.0, max_accel_mps2=3.0)
    point = predict_ca(observations, config)[0]
    assert float(point["x"]) <= 20.0

