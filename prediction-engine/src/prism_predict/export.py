"""PredictionFrame assembly, JSON Schema validation, and command-line entrypoint."""

from __future__ import annotations

import argparse
import json
import math
from importlib.resources import files
from pathlib import Path
from typing import Mapping, Sequence

from jsonschema import Draft202012Validator

from .baseline import PredictionConfig
from .history import (
    load_vehicle_frames,
    normalize_history_to_current_ego,
    observations_for_vehicle,
)
from .modes import candidate_trajectories


def build_prediction_frame(
    normalized_frames: Sequence[Mapping[str, object]],
    config: PredictionConfig = PredictionConfig(),
    include_ego: bool = False,
) -> dict[str, object]:
    if not normalized_frames:
        raise ValueError("at least one normalized VehicleFrame is required")
    current = normalized_frames[-1]
    current_ids = [str(vehicle["id"]) for vehicle in current["vehicles"]]  # type: ignore[index]
    ego_id = str(current["ego_id"])
    predictions = []
    for vehicle_id in current_ids:
        if vehicle_id == ego_id and not include_ego:
            continue
        observations = observations_for_vehicle(normalized_frames, vehicle_id)
        predictions.append(
            {
                "vehicle_id": vehicle_id,
                "trajectories": candidate_trajectories(observations, config),
            }
        )
    result: dict[str, object] = {
        "schema_version": "1.0",
        "timestamp_ms": int(current["timestamp_ms"]),
        "predictions": predictions,
    }
    validate_prediction_frame(result, set(current_ids))
    return result


def _schema() -> dict[str, object]:
    schema_path = files("prism_predict").joinpath("schema/prediction_frame.schema.json")
    return json.loads(schema_path.read_text(encoding="utf-8"))


def validate_prediction_frame(
    prediction_frame: Mapping[str, object], known_vehicle_ids: set[str] | None = None
) -> None:
    Draft202012Validator(_schema()).validate(prediction_frame)
    for prediction in prediction_frame["predictions"]:  # type: ignore[index]
        vehicle_id = str(prediction["vehicle_id"])
        if known_vehicle_ids is not None and vehicle_id not in known_vehicle_ids:
            raise ValueError(f"prediction references an unknown vehicle id: {vehicle_id}")
        for trajectory in prediction["trajectories"]:
            for point in trajectory["points"]:
                for field in ("x", "y", "heading_rad"):
                    if not math.isfinite(float(point[field])):
                        raise ValueError(f"non-finite prediction value: {field}")


def export_file(
    input_path: str | Path,
    output_path: str | Path,
    config: PredictionConfig = PredictionConfig(),
    include_ego: bool = False,
) -> dict[str, object]:
    frames = load_vehicle_frames(input_path)
    normalized = normalize_history_to_current_ego(frames)
    result = build_prediction_frame(normalized, config, include_ego)
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return result


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="PRISM Part 01 trajectory predictor")
    subparsers = parser.add_subparsers(dest="command", required=True)
    export = subparsers.add_parser("export", help="convert VehicleFrame JSONL to PredictionFrame")
    export.add_argument("--input", required=True, help="VehicleFrame JSONL path")
    export.add_argument("--output", required=True, help="PredictionFrame JSON path")
    export.add_argument(
        "--horizons-ms",
        nargs="+",
        type=int,
        default=[1000, 2000, 3000, 4000, 5000],
    )
    export.add_argument("--include-ego", action="store_true")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    if args.command == "export":
        config = PredictionConfig(horizons_ms=tuple(args.horizons_ms))
        export_file(args.input, args.output, config, args.include_ego)
        return 0
    return 2


if __name__ == "__main__":
    raise SystemExit(main())

