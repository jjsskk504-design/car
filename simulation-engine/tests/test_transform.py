import math

import pytest

from adapters.ego_transform import to_ego, wrap_angle

HALF_PI = math.pi / 2
# 고정 예시 3종 (자차 이동·회전 포함). 입력은 HighwayEnv 월드(x=진행, y=오른쪽)
CASES = [
    # 자차 원점·정면, 오른쪽 차선 10m 앞 차량 -> PRISM (x=3.5 오른쪽, y=10 전방)
    ((0, 0, 0), (10, 3.5, 20, 0, 0), (3.5, 10, 0, 20, HALF_PI)),
    # 자차가 (100,4)로 이동, 왼쪽 차선(y=0) 20m 앞 차량 -> x=-4 (왼쪽)
    ((100, 4, 0), (120, 0, 25, 0, 0), (-4, 20, 0, 25, HALF_PI)),
    # 자차가 오른쪽으로 0.1rad 회전, 자차 진행축 10m 앞 heading 0 차량 -> 상대적으로 왼쪽으로 0.1 회전
    ((0, 0, 0.1), (10 * math.cos(0.1), 10 * math.sin(0.1), 0, 0, 0), (0, 10, 0, 0, HALF_PI + 0.1)),
]


@pytest.mark.parametrize("ego,obj,expected", CASES)
def test_fixed_examples(ego, obj, expected):
    got = to_ego(*ego, *obj[:2], *obj[2:4], obj[4])
    assert got == pytest.approx(expected, abs=1e-9)


def test_ego_itself_is_origin_facing_forward():
    x, y, vx, vy, h = to_ego(50, 4, 0.2, 50, 4, 25 * math.cos(0.2), 25 * math.sin(0.2), 0.2)
    assert (x, y, vx, vy, h) == pytest.approx((0, 0, 0, 25, HALF_PI), abs=1e-9)


def test_wrap_angle():
    assert wrap_angle(3 * math.pi) == pytest.approx(math.pi)
    assert wrap_angle(-3 * math.pi / 2) == pytest.approx(math.pi / 2)
    assert -math.pi < wrap_angle(-math.pi) <= math.pi
