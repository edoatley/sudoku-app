"""
End-to-end tests for the Bedrock-backed handler.

Reads puzzle images listed in tests/e2e_config.json, encodes each as base64,
calls handler.handler() exactly as the Lambda runtime would, and validates the
returned 9×9 grid.

Requires:
  - AWS credentials with bedrock:InvokeModel permission (eu-west-2)
  - The fixture images listed in e2e_config.json to exist on disk

Run with:
    pytest tests/test_e2e_bedrock.py -v -m e2e

Skip automatically when AWS credentials are not available.
"""
from __future__ import annotations

import base64
import json
from pathlib import Path

import pytest

# ---------------------------------------------------------------------------
# Config loading
# ---------------------------------------------------------------------------

_CONFIG_PATH = Path(__file__).parent / "e2e_config.json"
_REPO_ROOT = Path(__file__).parent.parent


def _load_puzzle_params() -> list[tuple[str, Path, int]]:
    """Return (name, path, min_clues) for every puzzle in e2e_config.json."""
    with _CONFIG_PATH.open() as f:
        config = json.load(f)
    result = []
    for entry in config["puzzles"]:
        path = _REPO_ROOT / entry["file"]
        result.append((entry["name"], path, entry["min_clues"]))
    return result


_PUZZLES = _load_puzzle_params()
_PUZZLE_IDS = [name for name, _, _ in _PUZZLES]


# ---------------------------------------------------------------------------
# AWS availability check
# ---------------------------------------------------------------------------

def _aws_available() -> bool:
    try:
        import boto3
        from botocore.exceptions import NoCredentialsError, ClientError

        sts = boto3.client("sts", region_name="eu-west-2")
        sts.get_caller_identity()
        return True
    except Exception:
        return False


_AWS_AVAILABLE = _aws_available()

pytestmark = pytest.mark.e2e


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _encode_image(path: Path) -> str:
    """Read an image file and return its base64 encoding."""
    return base64.b64encode(path.read_bytes()).decode()


def _make_event(image_b64: str) -> dict:
    """Build a Lambda proxy event containing the base64 image."""
    return {"body": json.dumps({"image": image_b64})}


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

@pytest.mark.skipif(not _AWS_AVAILABLE, reason="AWS credentials not available")
class TestBedrockHandlerE2E:

    @pytest.mark.parametrize("name,path,min_clues", _PUZZLES, ids=_PUZZLE_IDS)
    def test_fixture_image_exists(self, name, path, min_clues):
        assert path.exists(), (
            f"{name}: fixture not found at {path}. "
            "Add the image or remove it from tests/e2e_config.json."
        )

    @pytest.mark.parametrize("name,path,min_clues", _PUZZLES, ids=_PUZZLE_IDS)
    def test_handler_returns_200(self, name, path, min_clues):
        if not path.exists():
            pytest.skip(f"{name}: fixture image not found at {path}")

        import handler

        event = _make_event(_encode_image(path))
        response = handler.handler(event, None)

        assert response["statusCode"] == 200, (
            f"{name}: handler returned {response['statusCode']} — "
            f"{response['body']}"
        )

    @pytest.mark.parametrize("name,path,min_clues", _PUZZLES, ids=_PUZZLE_IDS)
    def test_handler_returns_valid_9x9_grid(self, name, path, min_clues):
        if not path.exists():
            pytest.skip(f"{name}: fixture image not found at {path}")

        import handler

        event = _make_event(_encode_image(path))
        response = handler.handler(event, None)
        body = json.loads(response["body"])

        assert "originalGrid" in body, f"{name}: 'originalGrid' missing from response"
        grid = body["originalGrid"]
        assert len(grid) == 9, f"{name}: expected 9 rows, got {len(grid)}"
        for i, row in enumerate(grid):
            assert len(row) == 9, f"{name}: row {i} has {len(row)} cells, expected 9"

    @pytest.mark.parametrize("name,path,min_clues", _PUZZLES, ids=_PUZZLE_IDS)
    def test_grid_values_are_valid_digits(self, name, path, min_clues):
        if not path.exists():
            pytest.skip(f"{name}: fixture image not found at {path}")

        import handler

        event = _make_event(_encode_image(path))
        response = handler.handler(event, None)
        grid = json.loads(response["body"])["originalGrid"]

        for i, row in enumerate(grid):
            for j, val in enumerate(row):
                assert isinstance(val, int) and 0 <= val <= 9, (
                    f"{name}: cell [{i}][{j}] = {val!r} is not a valid digit (0–9)"
                )

    @pytest.mark.parametrize("name,path,min_clues", _PUZZLES, ids=_PUZZLE_IDS)
    def test_grid_has_minimum_clues(self, name, path, min_clues):
        if not path.exists():
            pytest.skip(f"{name}: fixture image not found at {path}")

        import handler

        event = _make_event(_encode_image(path))
        response = handler.handler(event, None)
        grid = json.loads(response["body"])["originalGrid"]

        clue_count = sum(v != 0 for row in grid for v in row)
        assert clue_count >= min_clues, (
            f"{name}: only {clue_count} clues detected, expected ≥ {min_clues}"
        )

    @pytest.mark.parametrize("name,path,min_clues", _PUZZLES, ids=_PUZZLE_IDS)
    def test_print_recognized_grid(self, name, path, min_clues, capsys):
        """Pretty-print each grid so results are visible in pytest -v output."""
        if not path.exists():
            pytest.skip(f"{name}: fixture image not found at {path}")

        import handler

        event = _make_event(_encode_image(path))
        response = handler.handler(event, None)
        body = json.loads(response["body"])

        print(f"\n=== {name} (status {response['statusCode']}) ===")
        if "originalGrid" in body:
            grid = body["originalGrid"]
            for r, row in enumerate(grid):
                if r % 3 == 0 and r != 0:
                    print("------+-------+------")
                print(
                    " ".join(
                        (str(v) if v else ".") + (" |" if c in (2, 5) else "")
                        for c, v in enumerate(row)
                    )
                )
        else:
            print(f"Error: {body.get('error', 'unknown')}")

        captured = capsys.readouterr()
        assert name in captured.out
