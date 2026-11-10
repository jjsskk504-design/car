"""사용: python -m generators scenarios/*.yaml -o out/"""
import argparse
import sys

from exporters.jsonl import write_outputs
from .scenario_runner import load, run


def main(argv=None):
    p = argparse.ArgumentParser(prog="python -m generators", description="PRISM VehicleFrame JSONL 생성기")
    p.add_argument("scenarios", nargs="+", help="시나리오 YAML 경로")
    p.add_argument("-o", "--out", default="out", help="출력 폴더 (기본 out/)")
    a = p.parse_args(argv)
    results = []
    for path in a.scenarios:
        frames, poses, m = run(load(path))
        print(f"{m['scenario']:<12} frames={m['frame_count']:<4} collisions={len(m['observed_collisions'])}")
        results.append((frames, poses, m))
    print("->", write_outputs(a.out, results))


if __name__ == "__main__":
    sys.exit(main())
