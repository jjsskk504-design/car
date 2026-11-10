import math

import pytest

from prism_predict.coordinates import Pose2D, transform_point


def test_translation_into_moving_ego_frame() -> None:
    result = transform_point(2.0, 12.0, Pose2D(0.0, 0.0, 0.0), Pose2D(0.0, 10.0, 0.0))
    assert result == pytest.approx((2.0, 2.0))


def test_ninety_degree_source_rotation() -> None:
    result = transform_point(1.0, 0.0, Pose2D(0.0, 0.0, math.pi / 2), Pose2D(0.0, 0.0, 0.0))
    assert result == pytest.approx((0.0, 1.0), abs=1e-9)


def test_translation_and_rotation_together() -> None:
    result = transform_point(
        2.0,
        1.0,
        Pose2D(10.0, 5.0, math.pi / 2),
        Pose2D(8.0, 8.0, -math.pi / 2),
    )
    assert result == pytest.approx((1.0, 1.0), abs=1e-9)

