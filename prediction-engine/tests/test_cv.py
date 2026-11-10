import math

import pytest

from prism_predict.baseline import PredictionConfig, predict_cv


def test_cv_uses_seconds_and_keeps_prism_axes() -> None:
    vehicle = {"x": 1.0, "y": 10.0, "vx": 2.0, "vy": 5.0, "heading_rad": 0.0}
    points = predict_cv(vehicle, PredictionConfig(horizons_ms=(1000, 3000)))
    assert points[0]["x"] == pytest.approx(3.0)
    assert points[0]["y"] == pytest.approx(15.0)
    assert points[1]["x"] == pytest.approx(7.0)
    assert points[1]["y"] == pytest.approx(25.0)
    assert points[0]["heading_rad"] == pytest.approx(math.atan2(5.0, 2.0))


def test_cv_retains_heading_when_stationary() -> None:
    vehicle = {"x": 0.0, "y": 0.0, "vx": 0.01, "vy": 0.01, "heading_rad": 1.2}
    point = predict_cv(vehicle, PredictionConfig(horizons_ms=(1000,)))[0]
    assert point["heading_rad"] == pytest.approx(1.2)


def test_prediction_horizon_beyond_five_seconds_is_rejected() -> None:
    with pytest.raises(ValueError, match="5000 ms"):
        PredictionConfig(horizons_ms=(6000,))

