"""HighwayEnv(MIT) 도로·차량 물리(IDM/MOBIL)를 라이브러리로 사용해 월드 상태를 생성한다.

gym 환경(step/action) 대신 Road를 직접 구성한다: 차량 배치와 이벤트 시각을 결정적으로 통제하기 위함.
"""
import numpy as np
from highway_env.road.road import Road, RoadNetwork
from highway_env.vehicle.behavior import IDMVehicle

ROAD_LEN_M = 10000.0
SUBSTEPS = 5  # step_ms 하나를 5번 나눠 적분 (step_ms=100 -> 20ms)
SIM_EVENTS = {"set_target_speed", "change_lane"}


def _lane(net, i):
    return net.get_lane(("0", "1", i))


def build_road(sc):
    net = RoadNetwork.straight_road_network(lanes=sc["lanes"], length=ROAD_LEN_M)
    road = Road(network=net, np_random=np.random.RandomState(sc["seed"]), record_history=False)
    vehicles = {}
    for spec in [dict(sc["ego"], id=sc.get("ego_id", "ego"))] + sc.get("vehicles", []):
        lane = _lane(net, spec["lane"])
        v = IDMVehicle(
            road,
            lane.position(spec["x"], 0),
            heading=lane.heading_at(spec["x"]),
            speed=spec["speed"],
            target_speed=spec.get("target_speed", spec["speed"]),
            enable_lane_change=spec.get("auto_lane_change", False),
        )
        # 클래스 상수를 인스턴스에서 덮어써 트럭 등 크기를 반영 (충돌 판정에도 사용됨)
        v.LENGTH = spec.get("length_m", v.LENGTH)
        v.WIDTH = spec.get("width_m", v.WIDTH)
        road.vehicles.append(v)
        vehicles[spec["id"]] = v
    return road, vehicles


def simulate(sc):
    """시나리오를 돌려 출력 스텝별 월드 상태 목록과 관측된 충돌 목록을 반환한다."""
    road, vehicles = build_road(sc)
    step_ms = sc["step_ms"]
    dt = step_ms / 1000.0 / SUBSTEPS
    n_steps = int(round(sc["duration_s"] * 1000 / step_ms))
    events = sorted(
        (e for e in sc.get("events", []) if e["type"] in SIM_EVENTS), key=lambda e: e["t_s"]
    )
    frames, collisions, crashed = [], [], set()
    for k in range(n_steps + 1):
        t_ms = k * step_ms
        while events and events[0]["t_s"] * 1000 <= t_ms:
            _apply(events.pop(0), vehicles)
        frames.append((t_ms, [_state(vid, v) for vid, v in vehicles.items()]))
        for vid, v in vehicles.items():
            if v.crashed and vid not in crashed:
                crashed.add(vid)
                collisions.append({"t_ms": t_ms, "vehicle_id": vid})
        if k < n_steps:
            for _ in range(SUBSTEPS):
                road.act()
                road.step(dt)
    return frames, collisions


def _apply(e, vehicles):
    v = vehicles[e["vehicle"]]
    if e["type"] == "set_target_speed":
        v.target_speed = float(e["value"])
    elif e["type"] == "change_lane":
        v.target_lane_index = ("0", "1", int(e["value"]))


def _state(vid, v):
    # v.velocity는 slip angle beta를 빠뜨림(원본 TODO). step()과 같은 실제 이동 방향 heading+beta 사용
    beta = np.arctan(0.5 * np.tan(v.action.get("steering", 0.0))) if v.action else 0.0
    vx, vy = v.speed * np.cos(v.heading + beta), v.speed * np.sin(v.heading + beta)
    return {
        "id": vid,
        "x": float(v.position[0]),
        "y": float(v.position[1]),
        "vx": float(vx),
        "vy": float(vy),
        "h": float(v.heading),
        "length_m": float(v.LENGTH),
        "width_m": float(v.WIDTH),
        "lane": int(v.lane_index[2]),
    }
