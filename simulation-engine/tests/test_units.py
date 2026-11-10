import json
import math
from pathlib import Path

import jsonschema
import pytest

SCHEMA = json.loads((Path(__file__).resolve().parents[1] / "schema/vehicle_frame.v1.schema.json").read_text(encoding="utf-8"))


def test_contract_example_from_spec_is_valid():
    # 지시서 02장 VehicleFrame v1 예시 그대로
    ex = {"schema_version": "1.0", "timestamp_ms": 1000, "coordinate_system": "ego_local_m", "ego_id": "ego", "vehicles": [
        {"id": "ego", "x": 0, "y": 0, "vx": 0, "vy": 25, "heading_rad": 1.570796, "length_m": 4.6, "width_m": 1.8, "lane_id": "L2"},
        {"id": "car_01", "x": 3.6, "y": 20, "vx": -0.3, "vy": 20, "heading_rad": 1.59, "length_m": 4.4, "width_m": 1.8, "lane_id": "L3"}]}
    jsonschema.validate(ex, SCHEMA)


def test_frames_match_schema_and_are_finite(result):
    frames, _, _ = result
    v = jsonschema.Draft202012Validator(SCHEMA)
    for f in frames:
        v.validate(f)
        for veh in f["vehicles"]:
            assert all(math.isfinite(veh[k]) for k in ("x", "y", "vx", "vy", "heading_rad"))
        json.dumps(f, allow_nan=False)


def test_time_axis(result):
    frames, _, m = result
    ts = [f["timestamp_ms"] for f in frames]
    assert ts[0] == 0
    assert all(b - a == m["step_ms"] for a, b in zip(ts, ts[1:]))
    assert ts[-1] >= 10_000  # 시나리오 최소 10초


def test_ego_is_origin_facing_forward(result):
    frames, _, _ = result
    for f in frames:
        ego = next(v for v in f["vehicles"] if v["id"] == f["ego_id"])
        assert (ego["x"], ego["y"], ego["vx"]) == pytest.approx((0, 0, 0), abs=1e-6)
        assert ego["heading_rad"] == pytest.approx(math.pi / 2, abs=1e-6)


def test_speed_units_are_m_per_s(result):
    # 자차 vy(m/s) == 월드 자차 위치 변화(m) / 시간(s): 단위(m, m/s, ms) 일관성 검증
    frames, poses, m = result
    dt = m["step_ms"] / 1000
    for i in range(len(frames) - 1):
        vy0 = next(v for v in frames[i]["vehicles"] if v["id"] == "ego")["vy"]
        vy1 = next(v for v in frames[i + 1]["vehicles"] if v["id"] == "ego")["vy"]
        moved = math.dist((poses[i]["x"], poses[i]["y"]), (poses[i + 1]["x"], poses[i + 1]["y"]))
        assert moved / dt == pytest.approx((vy0 + vy1) / 2, abs=0.5)
    for f in frames:
        assert all(math.hypot(v["vx"], v["vy"]) < 60 for v in f["vehicles"])


def test_ego_pose_sidecar_reconstructs_motion(result):
    """01번용 보조 파일 검증: 로컬 위치+자차 자세로 복원한 월드 위치의 변화가 속도*dt 와 일치."""
    frames, poses, m = result
    dt = m["step_ms"] / 1000
    noisy = {e["vehicle_id"] for e in m["event_annotation"] if e["type"] == "sensor_noise"}

    def world(f, p):
        a = p["heading_rad"] - math.pi / 2
        c, s = math.cos(a), math.sin(a)
        out = {}
        for v in f["vehicles"]:
            out[v["id"]] = (p["x"] + c * v["x"] - s * v["y"], p["y"] + s * v["x"] + c * v["y"],
                            c * v["vx"] - s * v["vy"], s * v["vx"] + c * v["vy"])
        return out

    for i in range(len(frames) - 1):
        a, b = world(frames[i], poses[i]), world(frames[i + 1], poses[i + 1])
        for vid in (a.keys() & b.keys()) - noisy:
            # 조향이 계단식으로 바뀌면 구간 내 속도가 점프하므로, 이동량은 양 끝 속도*dt 사이에 있어야 한다
            for ax in (0, 1):
                moved = b[vid][ax] - a[vid][ax]
                lo, hi = sorted((a[vid][2 + ax] * dt, b[vid][2 + ax] * dt))
                assert lo - 0.05 <= moved <= hi + 0.05, (vid, i, ax, moved, lo, hi)
