from collections import Counter


def test_ids_unique_and_stable(result):
    frames, _, m = result
    declared = set(m["vehicle_ids"])
    for f in frames:
        ids = [v["id"] for v in f["vehicles"]]
        assert len(ids) == len(set(ids))
        assert set(ids) <= declared
        assert f["ego_id"] in ids


def test_missing_only_inside_annotated_dropout(result):
    """관측 누락은 매니페스트의 의도적 sensor_dropout 구간에서만 허용(그 외는 결함)."""
    frames, _, m = result
    windows = [e for e in m["event_annotation"] if e["type"] == "sensor_dropout"]
    for f in frames:
        present = {v["id"] for v in f["vehicles"]}
        for vid in set(m["vehicle_ids"]) - present:
            t = f["timestamp_ms"]
            assert any(e["vehicle_id"] == vid and e["t_ms"] <= t < e["t_end_ms"] for e in windows), (vid, t)
            assert all(e["intentional"] for e in windows)


def test_dropout_scenario_actually_drops():
    from generators.scenario_runner import load, run
    from conftest import ROOT
    frames, _, _ = run(load(ROOT / "scenarios/sensor_dropout.yaml"))
    seen = Counter(v["id"] for f in frames for v in f["vehicles"])
    assert seen["car_01"] == len(frames) - 20  # 3~5s, 100ms 간격 -> 20프레임 누락
    assert seen["car_03"] == len(frames) - 5


def test_lane_ids_in_map(result):
    frames, _, m = result
    assert {v["lane_id"] for f in frames for v in f["vehicles"]} <= set(m["lane_id_map"])


def test_manifest_metadata_complete(result):
    """프롬프트 5항 메타데이터: duration, timestep, seed, 차량 수, 이벤트 시점, 자차 ID, 데이터 출처."""
    _, _, m = result
    for k in ("duration_ms", "step_ms", "sim_substep_ms", "seed", "vehicle_count", "event_annotation", "ego_id", "provenance"):
        assert k in m, k
    assert m["vehicle_count"] == len(m["vehicle_ids"])
    assert "synthetic" in m["provenance"]["data_source"]
    assert all(e["origin"] == "forced" and "t_ms" in e for e in m["event_annotation"])


def test_expected_output_files():
    from conftest import ROOT
    names = {p.name for p in (ROOT / "samples").glob("*.jsonl") if not p.name.endswith(".ego_pose.jsonl")}
    assert names == {f"{n}.jsonl" for n in ("normal", "sudden_braking", "rear_approach", "lane_change",
                                            "merge", "chain_reaction", "sensor_dropout")}
