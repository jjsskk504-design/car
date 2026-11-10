"""JSONL/매니페스트 저장. allow_nan=False 로 NaN/Inf 가 파일에 들어가는 것을 막는다."""
import json
from pathlib import Path


def write_jsonl(path, rows):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False, allow_nan=False, separators=(",", ":")) + "\n")


def write_outputs(out_dir, results):
    """results: [(frames, poses, manifest_entry)] -> out_dir/*.jsonl + scenario_manifest.json"""
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    for frames, poses, m in results:
        write_jsonl(out / m["file"], frames)
        write_jsonl(out / m["ego_pose_file"], poses)
    manifest = {"schema_version": "1.0", "scenarios": [m for _, _, m in results]}
    (out / "scenario_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, allow_nan=False, indent=2), encoding="utf-8"
    )
    return out


def read_jsonl(path):
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]
