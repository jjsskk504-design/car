import math

from prism_predict.baseline import PredictionConfig
from prism_predict.modes import candidate_trajectories


def test_lane_change_is_smooth_and_bounded_by_lane_width() -> None:
    observations = [
        (0, {"x": 0.0, "y": 10.0, "vx": 0.0, "vy": 5.0, "heading_rad": math.pi / 2})
    ]
    config = PredictionConfig(horizons_ms=(1000, 2000, 4000), lane_width_m=3.5)
    trajectories = candidate_trajectories(observations, config)
    keep = trajectories[0]["points"]
    left = trajectories[2]["points"]
    offsets = [float(keep_point["x"]) - float(left_point["x"]) for keep_point, left_point in zip(keep, left)]
    assert 0.0 < offsets[0] < offsets[1] < offsets[2]
    assert offsets[-1] == 3.5


def test_curvature_changes_position_and_heading() -> None:
    observations = [
        (0, {"x": 0.0, "y": 0.0, "vx": 5.0, "vy": 0.0, "heading_rad": 0.0})
    ]
    config = PredictionConfig(horizons_ms=(1000,), road_curvature_per_m=0.1)
    point = candidate_trajectories(observations, config)[0]["points"][0]
    assert float(point["y"]) > 0.0
    assert float(point["heading_rad"]) > 0.0

