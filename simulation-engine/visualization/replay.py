"""생성된 VehicleFrame JSONL을 2D로 재생한다 (자차 기준 탑뷰).

사용: python -m visualization.replay samples docs/replay
  -> <name>.gif (재생 영상), <name>.png (시간별 스냅샷), ffmpeg가 있으면 <name>.mp4도
"""
import json
import shutil
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.animation import FFMpegWriter, FuncAnimation, PillowWriter  # noqa: E402
from matplotlib.patches import Rectangle  # noqa: E402
from matplotlib.transforms import Affine2D  # noqa: E402

SNAP_S = [0, 3, 5, 7, 9, 12]
FRAME_STRIDE = 2  # GIF 용량 절감: 100ms 프레임 중 2개마다 1장 (= 5fps 실시간)


def draw(ax, frame, active=()):
    ax.clear()
    for x in (-6, -2, 2, 6):  # 4m 차선 경계 (자차가 차선 중앙이라는 근사)
        ax.axvline(x, color="#bbb", lw=0.8, ls="--")
    for v in frame["vehicles"]:
        ego = v["id"] == frame["ego_id"]
        L, W = v["length_m"], v["width_m"]
        r = Rectangle((-L / 2, -W / 2), L, W, fc="#2b6cb0" if ego else "#999", ec="k", lw=0.6)
        r.set_transform(Affine2D().rotate(v["heading_rad"]).translate(v["x"], v["y"]) + ax.transData)
        ax.add_patch(r)
        ax.text(v["x"] + 1.4, v["y"], v["id"], fontsize=6, clip_on=True)
    ax.set_xlim(-9, 9)
    ax.set_ylim(-45, 75)
    ax.set_aspect("equal")
    ax.tick_params(labelsize=6)
    label = f"t={frame['timestamp_ms'] / 1000:.1f}s"
    if active:
        label += "\n" + "\n".join(active)
    ax.set_title(label, fontsize=7)


def active_events(m, t_ms):
    """t_ms 시점에 진행 중이거나 막 발생한(1초 이내) 강제 이벤트 표시용 문자열."""
    out = []
    for e in m["event_annotation"]:
        end = e.get("t_end_ms", e["t_ms"] + 1000)
        if e["t_ms"] <= t_ms < end:
            out.append(f"[forced] {e['type']} {e['vehicle_id']}")
    return out


def load(sample_dir, m):
    with open(Path(sample_dir) / m["file"], encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def snapshots(frames, m, path):
    by_t = {f["timestamp_ms"]: f for f in frames}
    fig, axes = plt.subplots(1, len(SNAP_S), figsize=(2 * len(SNAP_S), 5.5))
    for ax, s in zip(axes, SNAP_S):
        t = min(s * 1000, m["duration_ms"])
        draw(ax, by_t[t], active_events(m, t))
    fig.suptitle(f"{m['scenario']} (ego_local_m: x=right, y=forward) - SIMULATION", fontsize=9)
    fig.tight_layout()
    fig.savefig(path, dpi=110)
    plt.close(fig)


def animate(frames, m, path):
    fig, ax = plt.subplots(figsize=(3, 5.2))
    fig.suptitle(f"{m['scenario']} - SIMULATION", fontsize=8)
    shown = frames[::FRAME_STRIDE]
    anim = FuncAnimation(fig, lambda f: draw(ax, f, active_events(m, f["timestamp_ms"])), frames=shown)
    fps = 1000 / (m["step_ms"] * FRAME_STRIDE)
    anim.save(path, writer=PillowWriter(fps=fps), dpi=70)
    if shutil.which("ffmpeg"):
        anim.save(Path(path).with_suffix(".mp4"), writer=FFMpegWriter(fps=fps), dpi=100)
    plt.close(fig)


def main(sample_dir="samples", out_dir="docs/replay"):
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((Path(sample_dir) / "scenario_manifest.json").read_text(encoding="utf-8"))
    for m in manifest["scenarios"]:
        frames = load(sample_dir, m)
        snapshots(frames, m, out / f"{m['scenario']}.png")
        animate(frames, m, out / f"{m['scenario']}.gif")
        print(out / m["scenario"], "(.png, .gif)", "+ .mp4" if shutil.which("ffmpeg") else "(ffmpeg 없음: mp4 생략)")


if __name__ == "__main__":
    main(*sys.argv[1:])
