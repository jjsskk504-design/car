"""PRISM vehicle-integration Mock 시나리오 생성기.

03(시뮬레이션)·01(예측)·02(위험) 엔진 산출물이 도착하기 전, 앱 통합을 검증하기 위한 MOCK 데이터다.
- 차량 움직임: 단순 결정적 운동 스크립트 (HighwayEnv 아님)
- 예측: 등속/등가속 외삽 (01번 엔진 아님)
- 위험: 간단한 TTC·간격 임계값 라벨 (02번 엔진 아님, 검증된 위험도 아님)
실제 엔진 결과가 오면 app/src/main/assets/scenarios/ 의 파일만 교체한다.

사용: python tests/tools/make_mock_scenarios.py   (vehicle-integration 폴더에서)
랜덤 없음 → 항상 같은 출력.
"""
import json
import math
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app" / "src" / "main" / "assets" / "scenarios"
FIXTURES = ROOT / "tests" / "fixtures" / "scenarios"
STEP_MS = 100
DT = STEP_MS / 1000
LANE = {"L1": -3.6, "L2": 0.0, "L3": 3.6}
RANK = ["NORMAL", "WATCH", "WARNING"]
PROVENANCE ="MOCK: vehicle-integration/tests/tools/make_mock_scenarios.py (03/01/02 엔진 산출물 아님)"


class Car:
    def __init__(self, cid, lane, y, v, length=4.6, width=1.8, accel=None, lane_plan=None, follow=False):
        self.id, self.x, self.y, self.v = cid, LANE[lane], y, v
        self.length, self.width = length, width
        self.accel_fn = accel or (lambda t: 0.0)  # 스크립트 가속도 (m/s^2)
        self.lane_plan = lane_plan or []          # [(시작 t, 목표 차선, 소요 s)]
        self.follow = follow                      # 같은 차선 앞차를 따라가는 단순 추종
        self.a = 0.0
        self.vx = 0.0
        self.lc = None  # 진행 중인 차선 변경 (시작 t, 시작 x, 목표 x, 소요 s)

    def lane_id(self):
        return min(LANE, key=lambda k: abs(LANE[k] - self.x))


def lead_of(car, cars):
    ahead = [c for c in cars if c is not car and abs(c.x - car.x) < 1.8 and c.y > car.y]
    return min(ahead, key=lambda c: c.y) if ahead else None


def step(cars, t, cruise):
    for c in cars:
        a = c.accel_fn(t)
        if c.follow:
            lead = lead_of(c, cars)
            a = 0.8 * (cruise.get(c.id, 25.0) - c.v)
            if lead:
                gap = lead.y - c.y - (lead.length + c.length) / 2
                desired = 5 + 1.2 * c.v
                if gap < desired * 1.5:
                    a = min(a, 0.6 * (gap - desired) + 1.2 * (lead.v - c.v))
            a = max(-7.0, min(1.5, a))
        c.a = a
        for start, lane, dur in c.lane_plan:
            if abs(t - start) < DT / 2:
                c.lc = (t, c.x, LANE[lane], dur)
    for c in cars:
        c.v = max(0.0, c.v + c.a * DT)
        if c.lc:
            # 최소 저크(5차 다항식) 차선 변경: 횡속도가 0 에서 시작해 0 으로 끝나고 목표 차선 중앙에 정렬된다
            t0, x0, x1, dur = c.lc
            s = min(max((t - t0) / dur, 0.0), 1.0)
            c.x = x0 + (x1 - x0) * (10 * s ** 3 - 15 * s ** 4 + 6 * s ** 5)
            c.vx = (x1 - x0) * (30 * s ** 2 - 60 * s ** 3 + 30 * s ** 4) / dur if s < 1.0 else 0.0
            if s >= 1.0:
                c.x, c.lc = x1, None
        else:
            c.vx = 0.0
        c.y += c.v * DT


def r3(v):
    return round(v, 3)


def vehicle_json(c, ego):
    return {
        "id": c.id, "x": r3(c.x - ego.x), "y": r3(c.y - ego.y),
        "vx": r3(c.vx), "vy": r3(c.v),
        "heading_rad": r3(math.atan2(c.v, c.vx) if (c.v or c.vx) else math.pi / 2),
        "length_m": c.length, "width_m": c.width, "lane_id": c.lane_id(),
    }


LANE_W = 3.6
LAT_TAU = 4.0 / 3.0  # 01 prediction-engine 의 lane_keep 횡방향 모델과 같은 시간상수(laneChangeDurationS/3)


def predict(v, a):
    """MOCK 예측: lane_keep(등속) + 감속 중이면 braking(등가속).
    횡방향은 현재 횡속도가 LAT_TAU 로 줄어드는 차선 정렬 모델(총 횡이동 한 차선 이내) — 여러 차선을 가로지르지 않는다."""
    def pts(acc):
        out = []
        for k in range(1, 6):
            t = float(k)
            if acc < 0 and v["vy"] > 0:
                t_stop = v["vy"] / -acc
                tt = min(t, t_stop)
                dy = v["vy"] * tt + 0.5 * acc * tt * tt
                vy_t = v["vy"] + acc * tt
            else:
                dy = v["vy"] * t
                vy_t = v["vy"]
            raw = v["vx"] * LAT_TAU * (1 - math.exp(-t / LAT_TAU))
            lat = max(-LANE_W, min(LANE_W, raw))
            vx_t = 0.0 if abs(raw) >= LANE_W else v["vx"] * math.exp(-t / LAT_TAU)
            heading = v["heading_rad"] if v["vx"] == 0 else r3(math.atan2(vy_t, vx_t))
            out.append({"dt_ms": k * 1000, "x": r3(v["x"] + lat), "y": r3(v["y"] + dy), "heading_rad": heading})
        return out
    trajs = [{"mode": "lane_keep", "confidence": None, "points": pts(0.0)}]
    if a < -1.0:
        trajs.append({"mode": "braking", "confidence": None, "points": pts(a)})
    return trajs


def box(cx, cy, w, l):
    return [{"x": r3(cx - w / 2), "y": r3(cy - l / 2)}, {"x": r3(cx + w / 2), "y": r3(cy - l / 2)},
            {"x": r3(cx + w / 2), "y": r3(cy + l / 2)}, {"x": r3(cx - w / 2), "y": r3(cy + l / 2)}]


def assess(v, ego_v, ego_len, accel, lead_accel):
    """MOCK 위험 라벨. 02번 엔진의 판정 로직이 아니다."""
    reasons, ttc = [], None
    same_lane = abs(v["x"]) < 1.8
    lateral_in = abs(v["x"]) >= 1.8 and v["vx"] * v["x"] < -0.2 and abs(v["y"]) < 30
    gap = abs(v["y"]) - (v_len := 4.6) / 2 - ego_len / 2
    level = "NORMAL"
    if same_lane:
        closing = (ego_v - v["vy"]) if v["y"] > 0 else (v["vy"] - ego_v)
        if closing > 0.1:
            ttc = max(gap, 0) / closing
        if (ttc is not None and ttc < 2.5) or gap < 8:
            level = "WARNING"
        elif (ttc is not None and ttc < 5.0) or gap < 15:
            level = "WATCH"
        if level != "NORMAL":
            reasons.append("FRONT_CLOSING" if v["y"] > 0 else "REAR_CLOSING")
        if v["y"] > 0 and accel < -3:
            reasons.append("HARD_BRAKING")
            level = max(level, "WARNING" if gap < 20 or level == "WATCH" else "WATCH", key=RANK.index)
        if v["y"] > 0 and lead_accel is not None and lead_accel < -3:
            reasons.append("CHAIN_DECEL")
            level = max(level, "WATCH", key=RANK.index)
    elif lateral_in:
        level = "WATCH" if abs(v["y"]) > 12 else "WARNING"
        reasons += ["LANE_CHANGE_INTRUSION", "PATH_OVERLAP"]
    if level == "NORMAL":
        return {"vehicle_id": v["id"], "risk_level": "NORMAL", "reason_codes": [], "ttc_s": None, "risk_zones": []}
    zone_x = v["x"] + v["vx"] * 1.0
    zone_y = v["y"] + v["vy"] * 1.0 - ego_v * 0  # source timestamp 좌표계 고정
    return {"vehicle_id": v["id"], "risk_level": level, "reason_codes": reasons,
            "ttc_s": r3(ttc) if ttc is not None else None,
            "risk_zones": [{"dt_ms": 1000, "polygon": box(zone_x, zone_y, 2.4, 7.0)}]}


def run(sid, title, cars, events, cruise=None, drop_frames=(), drop_vehicle=None, unknown=None, duration_s=10.0):
    cruise = cruise or {}
    ego = cars[0]
    vf, pf, rf = [], [], []
    n = int(duration_s / DT)
    for i in range(n + 1):
        t = i * DT
        ts = i * STEP_MS
        if i > 0:
            step(cars, t, cruise)
        if any(a <= ts < b for a, b in drop_frames):
            continue
        present = [c for c in cars if not (drop_vehicle and c.id == drop_vehicle[0] and drop_vehicle[1] <= ts < drop_vehicle[2])]
        vs = [vehicle_json(c, ego) for c in present]
        vf.append({"schema_version": "1.0", "timestamp_ms": ts, "coordinate_system": "ego_local_m", "ego_id": "ego", "vehicles": vs})
        others = [(c, v) for c, v in zip(present, vs) if c is not ego]
        pf.append({"schema_version": "1.0", "timestamp_ms": ts,
                   "predictions": [{"vehicle_id": v["id"], "trajectories": predict(v, c.a)} for c, v in others]})
        objs = []
        for c, v in others:
            lead = lead_of(c, cars)
            if unknown and c.id == unknown[0] and unknown[1] <= ts < unknown[2]:
                objs.append({"vehicle_id": c.id, "risk_level": "UNKNOWN", "reason_codes": ["SENSOR_DROPOUT"], "ttc_s": None, "risk_zones": []})
            else:
                objs.append(assess(v, ego.v, ego.length, c.a, lead.a if lead else None))
        rf.append({"schema_version": "1.0", "timestamp_ms": ts, "risk_objects": objs})
    manifest = {"scenario_id": sid, "title": title, "duration_ms": n * STEP_MS, "step_ms": STEP_MS, "seed": 0,
                "ego_id": "ego", "event_annotation": [{"t_ms": a, "label": b} for a, b in events],
                "provenance": PROVENANCE, "mock": True}
    write(OUT / sid, manifest, vf, pf, rf)
    return manifest, vf, pf, rf


def write(d, manifest, vf, pf, rf):
    if d.exists():
        shutil.rmtree(d)
    d.mkdir(parents=True)
    (d / "scenario_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    for name, rows in (("vehicle_frames.jsonl", vf), ("prediction_frames.jsonl", pf), ("risk_frames.jsonl", rf)):
        if rows is not None:
            (d / name).write_text("\n".join(json.dumps(r, ensure_ascii=False, separators=(",", ":")) for r in rows) + "\n", encoding="utf-8")


def brake(t0, t1, a):
    return lambda t: a if t0 <= t < t1 else 0.0


def ego():
    return Car("ego", "L2", 0.0, 25.0, follow=True)


def main():
    scenarios = []
    scenarios.append(run("normal", "정상 주행", [
        ego(), Car("car_01", "L2", 45, 25), Car("car_02", "L1", 15, 26), Car("car_03", "L3", -10, 24),
        Car("car_04", "L1", -25, 25), Car("car_05", "L3", 30, 25)],
        [(0, "정상 주행, 5대 일정 속도")]))
    scenarios.append(run("rear_approach", "뒤차 급접근", [
        # car_r 은 4.1s 에 자차 위치에 닿으므로 그 전에 L1 로 피한다. car_02 는 L1 에서 car_r 에 따라잡히지 않을 만큼 앞에 둔다.
        ego(), Car("car_r", "L2", -45, 36, lane_plan=[(2.0, "L1", 2.0)]), Car("car_02", "L1", 75, 25),
        Car("car_03", "L3", 10, 24), Car("car_04", "L3", -20, 25), Car("car_05", "L1", -30, 24)],
        [(0, "후방 car_r 36 m/s 로 접근"), (2000, "car_r 좌측 차선으로 회피")]))
    scenarios.append(run("sudden_brake", "앞차 급감속", [
        ego(), Car("car_01", "L2", 32, 25, accel=brake(3.0, 5.5, -7.0)), Car("car_02", "L1", 10, 25),
        Car("car_03", "L3", -5, 25), Car("car_04", "L1", -25, 25), Car("car_05", "L3", 35, 25)],
        [(3000, "car_01 급감속 -7 m/s²"), (5500, "car_01 감속 종료")]))
    scenarios.append(run("lane_change", "옆 차선 끼어들기", [
        # 위험 연출용 끼어들기: 간격이 부족한 상태에서 들어오지만, 궤적 자체는 3.5초 곡선 차선 변경(최소 저크)이다
        ego(), Car("car_02", "L3", 18, 23, lane_plan=[(3.0, "L2", 3.5)]), Car("car_01", "L2", 50, 25),
        Car("car_03", "L1", 5, 25), Car("car_04", "L1", -20, 25), Car("car_05", "L3", -15, 25)],
        [(3000, "car_02 간격이 부족한 상태로 자차 차선 끼어들기 시작"), (6500, "차선 변경 완료")]))
    scenarios.append(run("chain_reaction", "화물차 급감속 연쇄 위험", [
        ego(), Car("car_01", "L2", 24, 25, follow=True), Car("truck", "L2", 55, 24, length=12.0, width=2.5, accel=brake(2.5, 5.0, -6.0)),
        Car("car_02", "L1", 8, 25), Car("car_03", "L3", 30, 25, lane_plan=[(4.5, "L2", 3.0)]), Car("car_04", "L1", -20, 25)],
        [(2500, "truck 급감속"), (3200, "car_01 추종 감속"), (4500, "car_03 회피 차선 변경")],
        cruise={"ego": 25, "car_01": 25}))
    scenarios.append(run("dropout", "센서 누락·노이즈", [
        ego(), Car("car_01", "L2", 35, 25, accel=brake(3.0, 4.5, -3.0)), Car("car_02", "L1", 12, 25),
        Car("car_03", "L3", 20, 24), Car("car_04", "L1", -20, 25), Car("car_05", "L3", -30, 25)],
        [(2000, "car_03 센서 노이즈 (UNKNOWN)"), (4000, "전체 프레임 누락 4.0~5.5s"), (7000, "car_02 관측 누락 7.0~8.0s")],
        drop_frames=[(4000, 5500)], drop_vehicle=("car_02", 7000, 8000), unknown=("car_03", 2000, 3000)))

    OUT.mkdir(parents=True, exist_ok=True)
    index = OUT / "index.json"
    # importSim03 이 추가한 sim_* 항목은 유지한다(MOCK 만 다시 만든다)
    sims = [s for s in json.loads(index.read_text(encoding="utf-8")) if s.startswith("sim_")] if index.exists() else []
    index.write_text(json.dumps([m["scenario_id"] for m, *_ in scenarios] + sims, separators=(",", ":")), encoding="utf-8")
    make_fixtures(scenarios[0])
    for m, vf, pf, rf in scenarios:
        levels = {}
        for f in rf:
            for o in f["risk_objects"]:
                levels[o["risk_level"]] = levels.get(o["risk_level"], 0) + 1
        print(f"{m['scenario_id']:15s} frames={len(vf):3d} levels={levels}")


def make_fixtures(normal):
    """오류 처리 테스트용 소형 시나리오 (tests/fixtures/scenarios/<case>)."""
    m, vf, pf, rf = normal
    vf, pf, rf = vf[:5], pf[:5], rf[:5]
    m = dict(m, duration_ms=400)

    def case(name, manifest=m, v=vf, p=pf, r=rf, raw=None):
        d = FIXTURES / name
        write(d, manifest, v, p, r)
        for fname, text in (raw or {}).items():
            (d / fname).write_text(text, encoding="utf-8")

    case("valid")
    case("missing_vehicle_file", v=None)
    case("missing_prediction_file", p=None)
    case("bad_schema_version", v=[dict(f, schema_version="2.0") for f in vf])
    case("unknown_vehicle_ref", r=[dict(rf[0], risk_objects=[dict(rf[0]["risk_objects"][0], vehicle_id="ghost")])] + rf[1:])
    case("ego_missing", v=[dict(vf[0], vehicles=vf[0]["vehicles"][1:])] + vf[1:])
    case("stale_prediction", p=pf + [dict(pf[0], timestamp_ms=9999)])
    case("bad_risk_level", raw={"risk_frames.jsonl": json.dumps(dict(rf[0], risk_objects=[dict(rf[0]["risk_objects"][0], risk_level="DANGER")])) + "\n"})
    case("broken_json", raw={"vehicle_frames.jsonl": json.dumps(vf[0]) + "\n{\"schema_version\": \"1.0\", \"timestamp_ms\": \n"})
    case("gap", v=[vf[0], vf[4]], p=[pf[0], pf[4]], r=[rf[0], rf[4]])
    # 01/02 가 JSONL 대신 단일 .json 으로 줄 때: 예측은 프레임 하나, 위험은 프레임 배열
    case("single_json", p=None, r=None, raw={
        "prediction_frame.json": json.dumps(pf[2], ensure_ascii=False, indent=2),
        "risk_frames.json": json.dumps(rf, ensure_ascii=False, indent=2),
    })
    (FIXTURES.parent / "index.json").write_text("[]", encoding="utf-8")


if __name__ == "__main__":
    main()
