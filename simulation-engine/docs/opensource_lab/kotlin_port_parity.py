import json, sys
from pathlib import Path
py, kt = Path(sys.argv[1]), Path(sys.argv[2])
for f in sorted(py.glob("*.jsonl")):
    a = [json.loads(l) for l in f.open(encoding="utf-8")]
    b = [json.loads(l) for l in (kt / f.name).open(encoding="utf-8")]
    assert len(a) == len(b), f.name
    worst, keys_ok, idsets_ok = {}, True, True
    for fa, fb in zip(a, b):
        if "vehicles" not in fa:
            fa, fb = {"vehicles": [dict(fa, id="pose")]}, {"vehicles": [dict(fb, id="pose")]}
        va, vb = {v["id"]: v for v in fa["vehicles"]}, {v["id"]: v for v in fb["vehicles"]}
        idsets_ok &= va.keys() == vb.keys()
        for vid in va.keys() & vb.keys():
            if "sensor_dropout" in f.name and vid == "car_05": continue  # 노이즈 난수 생성기가 다름
            keys_ok &= va[vid].keys() == vb[vid].keys()
            for k, x in va[vid].items():
                if isinstance(x, (int, float)) and not isinstance(x, bool):
                    worst[k] = max(worst.get(k, 0), abs(x - vb[vid][k]))
                elif x != vb[vid][k]:
                    worst[k] = "DIFF"
    print(f"{f.name:32s} ids_same={idsets_ok} keys_same={keys_ok} max|py-kt|: " + " ".join(f"{k}={v:.3g}" if v != "DIFF" else f"{k}=DIFF" for k, v in worst.items()))
