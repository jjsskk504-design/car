"""HighwayEnv 월드 좌표 -> PRISM 자차(ego_local_m) 좌표 변환.

HighwayEnv 월드: x = 도로 진행 방향, y = 진행 방향의 오른쪽(차선 0,1,2가 y=0,4,8),
heading은 +x 기준이며 양수면 오른쪽으로 회전.
PRISM: x = 자차 오른쪽+, y = 자차 전방+, heading_rad은 +x에서 반시계(CCW), 전방 = pi/2.
"""
import math


def wrap_angle(a):
    """(-pi, pi] 범위로 정규화."""
    a = math.fmod(a + math.pi, 2 * math.pi)
    if a <= 0:
        a += 2 * math.pi
    return a - math.pi


def to_ego(ego_x, ego_y, ego_h, x, y, vx, vy, h):
    """월드 상태 하나를 자차 기준으로 바꾼다. 반환: (x, y, vx, vy, heading_rad).

    속도는 상대속도가 아니라 절대속도를 자차 축으로 회전한 값(계약 예시: ego vy=25).
    """
    c, s = math.cos(ego_h), math.sin(ego_h)
    dx, dy = x - ego_x, y - ego_y
    fwd = dx * c + dy * s
    right = -dx * s + dy * c
    v_fwd = vx * c + vy * s
    v_right = -vx * s + vy * c
    heading = wrap_angle(math.pi / 2 - (h - ego_h))
    return right, fwd, v_right, v_fwd, heading
