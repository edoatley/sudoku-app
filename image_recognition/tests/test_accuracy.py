"""Fixture-based recognition accuracy, per provider.

**A decision tool, not a gate.** It needs live provider credentials and costs money per run, so
it is marked `accuracy` and excluded from CI and the pre-push suite — exactly as `e2e` already
is. Its output is what the decision to flip IMAGE_AI_PROVIDER=vertex rests on, and belongs in
that PR's description.

Compares against a committed per-provider baseline rather than an absolute threshold: these
models are non-deterministic even at temperature 0, so an absolute bar produces flakes while a
regression check produces signal.

    pytest -m accuracy                          # current provider
    IMAGE_AI_PROVIDER=vertex pytest -m accuracy
    scripts/local/image-accuracy-compare.sh     # both, N runs, side by side

@spec IR-TEST-001, IR-TEST-002, IR-TEST-003
"""

from __future__ import annotations

import json
import os
import pathlib
import time
from datetime import UTC, datetime

import handler
import providers
import pytest

ROOT = pathlib.Path(__file__).resolve().parent
CONFIG = ROOT / "e2e_config.json"
BASELINE = ROOT / "accuracy_baseline.json"
REPORTS = ROOT / "reports"

pytestmark = pytest.mark.accuracy

THROTTLE_RETRIES = 4
THROTTLE_BACKOFF_SECONDS = 20
INTER_FIXTURE_SECONDS = 5
"""Paced deliberately. These runs are infrequent and manual, so wall-clock time is cheap and a
throttled result that masquerades as an accuracy regression is not."""


def _is_throttling(exc: object) -> bool:
    text = str(exc)
    return "429" in text or "RESOURCE_EXHAUSTED" in text or "Throttling" in text


def _fixtures() -> list[dict]:
    return json.loads(CONFIG.read_text())["puzzles"]


def _score(expected: list[list[int]], actual: list[list[int]]) -> dict:
    """Per-fixture metrics. Cell accuracy is the headline; the rest catch specific failure modes."""
    cells = 81
    matching = sum(
        1 for r in range(9) for c in range(9) if expected[r][c] == actual[r][c]
    )
    # The coloured-cell trap that motivated IR-PROC-013: a shaded but empty cell must stay 0.
    empties = [(r, c) for r in range(9) for c in range(9) if expected[r][c] == 0]
    empties_ok = all(actual[r][c] == 0 for r, c in empties)
    return {
        "cell_accuracy": matching / cells,
        "exact_grid": matching == cells,
        "valid_puzzle": not handler._has_row_col_box_duplicate(actual),
        "empty_cells_respected": empties_ok,
    }


@pytest.fixture(scope="module")
def results() -> dict:
    """Run every fixture once through the configured provider and return the scored results."""
    provider = providers.get_provider()
    per_fixture = {}

    for fixture in _fixtures():
        image = (ROOT.parent / fixture["file"]).read_bytes()
        grid = model = None
        last_error: Exception | None = None

        # Retry throttling, but never a recognition failure. Without this a 429 scores 0% and is
        # indistinguishable from the model having got dramatically worse, which turns the cutover
        # gate from a measurement into a coin toss. Quota is a property of the harness run, not
        # of the model under test.
        for attempt in range(THROTTLE_RETRIES):
            try:
                grid, _valid, model = handler._recognize(provider, image)
                break
            except Exception as exc:  # noqa: BLE001 — a failed read is a result, not an error
                last_error = exc
                grid = None
                if _is_throttling(exc) and attempt < THROTTLE_RETRIES - 1:
                    time.sleep(THROTTLE_BACKOFF_SECONDS * (attempt + 1))
                    continue
                break

        if grid is not None:
            per_fixture[fixture["name"]] = {
                **_score(fixture["expected_grid"], grid),
                "model": model,
            }
        else:
            per_fixture[fixture["name"]] = {
                "cell_accuracy": 0.0,
                "exact_grid": False,
                "valid_puzzle": False,
                "empty_cells_respected": False,
                "error": str(last_error)[:200],
                "throttled": _is_throttling(last_error),
            }
        time.sleep(INTER_FIXTURE_SECONDS)

    n = len(per_fixture) or 1
    summary = {
        "provider": provider.name,
        "models": provider.models,
        "timestamp": datetime.now(UTC).isoformat(),
        "fixtures": per_fixture,
        "mean_cell_accuracy": sum(f["cell_accuracy"] for f in per_fixture.values()) / n,
        "exact_grids": sum(1 for f in per_fixture.values() if f["exact_grid"]),
        "valid_puzzles": sum(1 for f in per_fixture.values() if f["valid_puzzle"]),
        "total": n,
    }

    REPORTS.mkdir(exist_ok=True)
    stamp = summary["timestamp"].replace(":", "-")
    (REPORTS / f"{provider.name}-{stamp}.json").write_text(
        json.dumps(summary, indent=2)
    )
    return summary


def test_report_the_run(results, capsys):
    """Always prints. The numbers are the point of running this at all."""
    with capsys.disabled():
        print(
            f"\n  provider: {results['provider']}  models: {', '.join(results['models'])}"
        )
        print(f"  mean cell accuracy: {results['mean_cell_accuracy']:.1%}")
        print(f"  exact grids:  {results['exact_grids']}/{results['total']}")
        print(f"  valid puzzles: {results['valid_puzzles']}/{results['total']}")
        for name, f in results["fixtures"].items():
            flag = (
                "OK "
                if f["exact_grid"]
                else ("   " if f["cell_accuracy"] > 0.9 else "!! ")
            )
            err = ""
            if "error" in f:
                err = f"  [{'THROTTLED' if f.get('throttled') else f['error'][:70]}]"
            print(f"    {flag}{name:24} {f['cell_accuracy']:6.1%}{err}")


def _throttled(results) -> list[str]:
    return [n for n, f in results["fixtures"].items() if f.get("throttled")]


def test_no_regression_against_the_baseline(results):
    """A regression check, not an absolute bar — these models vary run to run even at
    temperature 0, so an absolute threshold would flake. @spec IR-TEST-002"""
    throttled = _throttled(results)
    if throttled:
        pytest.skip(
            f"throttled on {throttled} after {THROTTLE_RETRIES} attempts — a rate limit is not "
            "an accuracy regression, so this run cannot be compared"
        )
    if not BASELINE.exists():
        pytest.skip(f"no baseline yet — write {BASELINE.name} from a green run")
    baseline = json.loads(BASELINE.read_text()).get(results["provider"])
    if not baseline:
        pytest.skip(f"no baseline recorded for provider {results['provider']!r}")

    assert results["mean_cell_accuracy"] >= baseline["mean_cell_accuracy"] - 0.02, (
        f"mean cell accuracy regressed: {results['mean_cell_accuracy']:.1%} vs baseline "
        f"{baseline['mean_cell_accuracy']:.1%}"
    )
    assert results["exact_grids"] >= baseline["exact_grids"], (
        f"exact grids regressed: {results['exact_grids']} vs baseline {baseline['exact_grids']}"
    )


@pytest.mark.skipif(
    os.environ.get("IMAGE_AI_PROVIDER", "bedrock").lower() != "vertex",
    reason="the cutover gate applies to the Vertex run only",
)
def test_vertex_meets_the_cutover_gate(results):
    """The decision this whole harness exists to inform.

    Vertex must be at least as good as the Bedrock baseline on both headline metrics. If it is
    not, the switch stays on bedrock, the cross-cloud AWS key survives, and CP-GCP-085 cannot be
    retired — see the Phase 5 plan §5.
    """
    throttled = _throttled(results)
    if throttled:
        pytest.skip(
            f"throttled on {throttled} — cannot judge the gate on a throttled run"
        )
    if not BASELINE.exists():
        pytest.skip("no baseline to compare against")
    bedrock = json.loads(BASELINE.read_text()).get("bedrock")
    if not bedrock:
        pytest.skip("no bedrock baseline recorded")

    assert results["mean_cell_accuracy"] >= bedrock["mean_cell_accuracy"], (
        f"Vertex {results['mean_cell_accuracy']:.1%} < Bedrock {bedrock['mean_cell_accuracy']:.1%}"
    )
    assert results["exact_grids"] >= bedrock["exact_grids"], (
        f"Vertex {results['exact_grids']} exact grids < Bedrock {bedrock['exact_grids']}"
    )
