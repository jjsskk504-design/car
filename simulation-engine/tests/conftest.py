import functools
from pathlib import Path

import pytest

from generators.scenario_runner import load, run

ROOT = Path(__file__).resolve().parents[1]
SCENARIOS = sorted((ROOT / "scenarios").glob("*.yaml"))


@functools.lru_cache(maxsize=None)
def run_cached(path):
    return run(load(path))


@pytest.fixture(params=SCENARIOS, ids=lambda p: p.stem)
def result(request):
    return run_cached(request.param)
