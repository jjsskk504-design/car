import json

from conftest import ROOT
from exporters.jsonl import read_jsonl
from generators.scenario_runner import load, run


def test_same_seed_same_output():
    sc = ROOT / "scenarios/sensor_dropout.yaml"  # 노이즈 포함 시나리오
    assert run(load(sc))[0] == run(load(sc))[0]


def test_seed_changes_noise():
    a = load(ROOT / "scenarios/sensor_dropout.yaml")
    b = dict(a, seed=a["seed"] + 1)
    assert run(a)[0] != run(b)[0]


def test_committed_samples_are_reproducible(result):
    """samples/ 의 파일이 현재 코드·seed로 그대로 재생성되는지 (오래된 샘플 방지)."""
    frames, poses, m = result
    assert read_jsonl(ROOT / "samples" / m["file"]) == json.loads(json.dumps(frames))
    assert read_jsonl(ROOT / "samples" / m["ego_pose_file"]) == json.loads(json.dumps(poses))
