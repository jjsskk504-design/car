"""YAML 시나리오 -> VehicleFrame v1 목록 + 매니페스트 항목."""
from importlib.metadata import version

import numpy as np
import yaml

from . import __version__
from adapters.ego_transform import to_ego
from adapters.highway_adapter import SIM_EVENTS, SUBSTEPS, simulate

OUTPUT_EVENTS = {"sensor_dropout", "sensor_noise"}
REQUIRED = ("name", "seed", "duration_s", "step_ms", "lanes", "ego")


def load(path):
    with open(path, encoding="utf-8") as f:
        sc = yaml.safe_load(f)
    missing = [k for k in REQUIRED if k not in sc]
    if missing:
        raise ValueError(f"{path}: 필수 키 누락 {missing}")
    ego_id = sc.setdefault("ego_id", "ego")
    ids = [ego_id] + [v["id"] for v in sc.get("vehicles", [])]
    if len(set(ids)) != len(ids):
        raise ValueError(f"{path}: 차량 id 중복")
    for e in sc.get("events", []):
        if e["type"] not in SIM_EVENTS | OUTPUT_EVENTS:
            raise ValueError(f"{path}: 알 수 없는 이벤트 {e['type']}")
        if e["vehicle"] not in ids:
            raise ValueError(f"{path}: 이벤트 대상 없음 {e['vehicle']}")
        if e["type"] == "sensor_dropout" and e["vehicle"] == ego_id:
            raise ValueError(f"{path}: 자차는 누락시킬 수 없음")
    return sc


def lane_id(i):
    return f"L{i + 1}"  # L1 = 가장 왼쪽 차선 (HighwayEnv lane 0)


def run(sc):
    """반환: (frames, ego_poses, manifest_entry)"""
    world_frames, collisions = simulate(sc)
    ego_id = sc["ego_id"]
    out_events = [e for e in sc.get("events", []) if e["type"] in OUTPUT_EVENTS]
    rng = np.random.default_rng(sc["seed"])
    frames, poses = [], []
    for t_ms, states in world_frames:
        ego = next(s for s in states if s["id"] == ego_id)
        vehicles = []
        for s in states:
            active = [e for e in out_events if e["vehicle"] == s["id"] and _in(e, t_ms)]
            if any(e["type"] == "sensor_dropout" for e in active):
                continue
            x, y, vx, vy, h = to_ego(ego["x"], ego["y"], ego["h"], s["x"], s["y"], s["vx"], s["vy"], s["h"])
            for e in active:  # sensor_noise: 위치·heading 가우시안 노이즈 (seed 고정)
                x += rng.normal(0, e["value"])
                y += rng.normal(0, e["value"])
                h += rng.normal(0, e.get("heading_std_rad", 0.02))
            vehicles.append({
                "id": s["id"], "x": round(x, 3), "y": round(y, 3),
                "vx": round(vx, 3), "vy": round(vy, 3), "heading_rad": round(h, 6),
                "length_m": s["length_m"], "width_m": s["width_m"], "lane_id": lane_id(s["lane"]),
            })
        frames.append({
            "schema_version": "1.0", "timestamp_ms": t_ms,
            "coordinate_system": "ego_local_m", "ego_id": ego_id, "vehicles": vehicles,
        })
        # 계약 외 보조 파일: 01번이 이전 프레임을 현재 자차 좌표로 옮길 때 쓰는 월드 자차 자세
        wx, wy, _, _, wh = to_ego(0, 0, 0, ego["x"], ego["y"], 0, 0, ego["h"])
        poses.append({"timestamp_ms": t_ms, "x": round(wx, 3), "y": round(wy, 3), "heading_rad": round(wh, 6)})
    return frames, poses, _manifest(sc, frames, collisions)


def _in(e, t_ms):
    return e["t_s"] * 1000 <= t_ms < e.get("t_end_s", float("inf")) * 1000


def _manifest(sc, frames, collisions):
    return {
        "scenario": sc["name"],
        "file": f"{sc['name']}.jsonl",
        "ego_pose_file": f"{sc['name']}.ego_pose.jsonl",
        "description": sc.get("description", ""),
        "duration_ms": frames[-1]["timestamp_ms"],
        "step_ms": sc["step_ms"],
        "sim_substep_ms": sc["step_ms"] / SUBSTEPS,
        "frame_count": len(frames),
        "seed": sc["seed"],
        "ego_id": sc["ego_id"],
        "vehicle_count": 1 + len(sc.get("vehicles", [])),
        "vehicle_ids": [sc["ego_id"]] + [v["id"] for v in sc.get("vehicles", [])],
        "lane_id_map": {lane_id(i): f"highway-env lane ('0','1',{i}), 왼쪽에서 {i + 1}번째" for i in range(sc["lanes"])},
        "event_annotation": [
            {k: v for k, v in {
                "t_ms": int(e["t_s"] * 1000),
                "t_end_ms": int(e["t_end_s"] * 1000) if "t_end_s" in e else None,
                "type": e["type"], "vehicle_id": e["vehicle"], "value": e.get("value"),
                "intentional": True, "origin": "forced", "note": e.get("note"),
            }.items() if v is not None}
            for e in sc.get("events", [])
        ],
        # 시뮬레이터가 실제로 판정한 충돌(정답 라벨). 위험도 판정이 아님.
        "observed_collisions": collisions,
        "provenance": {
            "engine": f"PRISM simulation-engine {__version__}",
            "simulator": f"highway-env {version('highway-env')} (MIT)",
            "data_source": "synthetic simulation (실제 주행·실차 ADAS 데이터 아님)",
            # 강제(forced) = event_annotation 에 적힌 것만. 그 외 모든 움직임은 시뮬레이터 자연 행동.
            "forced_behavior": "event_annotation 의 이벤트(목표속도 변경, 차선 변경 명령, 센서 누락·노이즈 주입)",
            "natural_behavior": "highway-env IDM 종방향 반응(감속·추종)과 차선 추종 조향. 자동 차선변경(MOBIL)은 꺼둠",
        },
    }
