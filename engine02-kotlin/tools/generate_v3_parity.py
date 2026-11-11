"""Generate the Kotlin V3 parity fixture from the checked-in Python oracle offline."""
from dataclasses import replace
from pathlib import Path
import sys

MODULE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(MODULE.parent / "engine02_preintegration_20261111" / "scripts"))
from behavior_v3.intent import IntentEngine, Neighbor, Observation


def obs(t, d=0., vd=0., **kwargs):
    s = kwargs.pop("s_m", t / 100.)
    speed = kwargs.pop("vs_mps", 10.)
    return Observation("actor", t, d, s, vd, speed, **kwargs)


def adjacent(t, d=3.6, gap=10., speed=4., ident="adjacent"):
    return Neighbor(ident, t, d, t / 100. + gap, 0., speed)


def scenarios():
    for name, sign in (("right", 1), ("left", -1)):
        yield name, [
            (0, obs(0)), (100, obs(100)),
            (200, obs(200, sign * .6, sign * .3)),
            (300, obs(300, sign * .65, sign * .8, yaw_rad=sign * .11)),
            (400, obs(400, sign * .65, -sign * .25)),
        ]
    signal = {"indicator": "LEFT", "indicator_provenance": "OBSERVED"}
    yield "signals", [
        (0, obs(0, **signal)),
        (100, obs(100, **signal)),
        (200, obs(200, indicator="RIGHT", indicator_provenance="SYNTHETIC")),
        (300, obs(300, indicator="LEFT", indicator_provenance="INFERRED")),
        (400, obs(400, indicator="OFF", indicator_provenance="OBSERVED", front_event="BRAKING")),
    ]
    yield "brake", [(0, obs(0)), (100, obs(100, vs_mps=9.7))]
    yield "yield", [
        (0, obs(0)), (100, obs(100, **signal)),
        (200, obs(200, vs_mps=9.7, neighbors=(adjacent(200),))),
    ]
    yield "geometry", [(0, obs(0, neighbors=(
        adjacent(0, d=0., gap=2., ident="overlap"),
        adjacent(0, d=0., gap=10., ident="future"),
        adjacent(0, d=-2., gap=-10., speed=14., ident="rear"),
        adjacent(1, ident="future_sample"),
        adjacent(0, ident="duplicate"), adjacent(0, ident="duplicate"),
        adjacent(0, speed=81., ident="fast"),
        replace(adjacent(0, ident="bad_shape"), width_m=.2),
        replace(adjacent(0, ident="rotated"), yaw_rad=.3),
    )))]
    yield "clock", [
        (-1, obs(0)), (0, obs(1)), (0, None), (0, obs(0)),
        (19, obs(19)), (20, obs(20)), (100, obs(100)),
        (100, obs(100)), (110, obs(90)), (109, obs(109)),
        (500, obs(200)), (1201, obs(1201)),
    ]
    yield "identity_and_lane", [
        (0, obs(0)), (100, obs(100)),
        (200, obs(200, identity_epoch="next")),
        (300, obs(300, identity_epoch="next")),
        (400, obs(400, identity_epoch="next", lane_reference="new")),
        (500, obs(500, identity_epoch="next", lane_reference="new", lane_center_d_m=3.6)),
        (600, obs(600, identity_epoch="next", lane_reference="new", lane_center_d_m=3.6, lane_width_m=4.)),
    ]
    yield "invalid", [
        (0, obs(0)), (100, obs(100, s_m=70.)),
        (200, obs(200, s_m=71., vs_mps=12.)),
        (300, obs(300, reliable=False)), (400, obs(400, lane_geometry_reliable=False)),
        (500, obs(500, length_m=31.)), (600, obs(600, vs_mps=81.)),
        (700, replace(obs(700), track_id="")), (800, obs(800)),
    ]


def atom(value):
    if value is None:
        return "~"
    if isinstance(value, bool):
        return str(value).lower()
    return str(value)


def flatten(value, prefix):
    if isinstance(value, dict):
        for key, item in value.items():
            yield from flatten(item, f"{prefix}.{key}" if prefix else key)
    elif isinstance(value, (list, tuple)):
        yield f"{prefix}.size", len(value)
        for index, item in enumerate(value):
            yield from flatten(item, f"{prefix}.{index}")
    else:
        yield prefix, value


def main():
    rows = []
    for name, samples in scenarios():
        engine = IntentEngine()
        for now, observation in samples:
            result = engine.observe(observation, now_ms=now, missing_track_id="missing")
            inputs = "~"
            if observation is not None:
                values = vars(observation)
                fields = [atom(value) for key, value in values.items() if key != "neighbors"]
                neighbors = [",".join(map(atom, vars(n).values())) for n in observation.neighbors]
                inputs = "|".join([",".join(fields), *neighbors])
            expected = ";;".join(f"{key}={atom(value)}" for key, value in flatten(result.to_dict(), ""))
            rows.append("\t".join((name, str(now), inputs, expected)))
    output = MODULE / "src" / "test" / "resources" / "behavior-v3-parity.tsv"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(rows) + "\n", encoding="utf-8", newline="\n")
    print(f"Python V3 oracle: {len(rows)} synthetic frames across {sum(1 for _ in scenarios())} scenarios")


if __name__ == "__main__":
    main()
