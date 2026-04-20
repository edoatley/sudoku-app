#!/usr/bin/env bash
# compare-image-recognition-models.sh
#
# Runs each Bedrock model independently, with and without PIL preprocessing,
# against all ground-truth fixtures and produces a comparison matrix.
#
# Usage:
#   bash scripts/local/compare-image-recognition-models.sh
#
# Requires:
#   aws sso login --profile sandbox

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IR_DIR="${REPO_ROOT}/image_recognition"
VENV="${IR_DIR}/.venv"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

echo -e "\n${BOLD}${CYAN}=== Checking AWS credentials (sandbox profile) ===${RESET}"
if ! AWS_PROFILE=sandbox aws sts get-caller-identity --output text &>/dev/null; then
  echo -e "${RED}✗ Not authenticated. Run: aws sso login --profile sandbox${RESET}"
  exit 1
fi
echo -e "${GREEN}✓ Authenticated${RESET}"

if [[ ! -f "${VENV}/bin/python" ]]; then
  echo "Setting up .venv..."
  python3 -m venv "${VENV}"
  "${VENV}/bin/pip" install -q -r "${IR_DIR}/requirements-dev.txt"
fi

echo -e "\n${BOLD}${CYAN}=== Model comparison (each model run independently) ===${RESET}\n"

(
  cd "${IR_DIR}"
  source "${VENV}/bin/activate"
  AWS_PROFILE=sandbox AWS_REGION_NAME=eu-west-2 python3 - <<'PYEOF'
import json, sys, logging
from pathlib import Path

# Suppress handler INFO logs — we want our own clean output
logging.getLogger("handler").setLevel(logging.WARNING)

sys.path.insert(0, ".")
import boto3
import handler as h

CONFIG = json.loads(Path("tests/e2e_config.json").read_text())
PUZZLES = CONFIG["puzzles"]

MODELS = [
    ("nemotron",  "nvidia.nemotron-nano-12b-v2"),
    ("haiku-4.5", "eu.anthropic.claude-haiku-4-5-20251001-v1:0"),
]
PIL_MODES = [("no-PIL", False), ("PIL", True)]

client = boto3.client("bedrock-runtime", region_name="eu-west-2")

def cell_accuracy(actual, expected):
    """Return (correct_cells, total_clue_cells, wrong_cells_list)."""
    correct = 0
    total = 0
    wrong = []
    for r in range(9):
        for c in range(9):
            exp = expected[r][c]
            act = actual[r][c]
            if exp != 0:
                total += 1
                if act == exp:
                    correct += 1
                else:
                    wrong.append((r, c, exp, act))
    return correct, total, wrong

def run_single(model_id, image_bytes, pil_enabled):
    """Run one model on one image, returning (grid, valid, error_str)."""
    h._MODELS = [model_id]
    if pil_enabled:
        processed = h._downscale_image(image_bytes)
    else:
        processed = image_bytes
    try:
        grid, valid, _ = h._recognize_with_bedrock(client, processed)
        return grid, valid, None
    except Exception as e:
        return None, False, str(e)

# ── Results table ─────────────────────────────────────────────────────────────
# results[puzzle_name][model_label] = {correct, total, wrong, valid, error}
results = {}

GREEN  = '\033[0;32m'
RED    = '\033[0;31m'
YELLOW = '\033[1;33m'
RESET  = '\033[0m'
BOLD   = '\033[1m'
CYAN   = '\033[0;36m'

col_labels = [f"{name}+{pil}" for name, _ in MODELS for pil, _ in PIL_MODES]

print(f"Running {len(PUZZLES)} puzzles × {len(MODELS)} models × {len(PIL_MODES)} PIL modes "
      f"= {len(PUZZLES)*len(MODELS)*len(PIL_MODES)} calls...\n")

for puzzle in PUZZLES:
    name = puzzle["name"]
    path = Path(puzzle["file"])
    expected = puzzle.get("expected_grid")
    image_bytes = path.read_bytes()
    results[name] = {}

    for model_name, model_id in MODELS:
        for pil_label, pil_on in PIL_MODES:
            key = f"{model_name}+{pil_label}"
            print(f"  {name} / {key} ...", end=" ", flush=True)
            grid, valid, err = run_single(model_id, image_bytes, pil_on)
            if err:
                results[name][key] = {"error": err}
                print(f"{RED}FAIL{RESET}")
            elif expected is None:
                results[name][key] = {"grid": grid, "valid": valid, "no_truth": True}
                print(f"{YELLOW}OK (no ground truth){RESET}")
            else:
                correct, total, wrong = cell_accuracy(grid, expected)
                results[name][key] = {
                    "grid": grid, "valid": valid,
                    "correct": correct, "total": total, "wrong": wrong,
                    "exact": len(wrong) == 0
                }
                status = f"{GREEN}✓ {correct}/{total}{RESET}" if not wrong else f"{RED}✗ {correct}/{total} ({len(wrong)} errors){RESET}"
                print(status)

# ── Summary matrix ────────────────────────────────────────────────────────────
print(f"\n{BOLD}{CYAN}{'='*72}{RESET}")
print(f"{BOLD}  Accuracy Matrix (correct clue cells / total clue cells){RESET}")
print(f"{BOLD}{CYAN}{'='*72}{RESET}")

# Header
hdr = f"{'Puzzle':<14}"
for lbl in col_labels:
    hdr += f"  {lbl:<18}"
print(f"\n{BOLD}{hdr}{RESET}")
print("-" * (14 + 20 * len(col_labels)))

exact_counts = {lbl: 0 for lbl in col_labels}
puzzle_count = 0

for puzzle in PUZZLES:
    name = puzzle["name"]
    puzzle_count += 1
    row = f"{name:<14}"
    for lbl in col_labels:
        r = results[name].get(lbl, {})
        if "error" in r:
            cell = f"{'FAIL':<18}"
            row += f"  {RED}{cell}{RESET}"
        elif "no_truth" in r:
            cell = f"{'(no truth)':<18}"
            row += f"  {YELLOW}{cell}{RESET}"
        else:
            c, t, w = r["correct"], r["total"], r["wrong"]
            pct = int(100 * c / t) if t else 0
            label = f"{c}/{t} ({pct}%)"
            if not w:
                exact_counts[lbl] += 1
                row += f"  {GREEN}{label:<18}{RESET}"
            else:
                row += f"  {RED}{label:<18}{RESET}"
    print(row)

print("-" * (14 + 20 * len(col_labels)))
# Totals row
tot = f"{'EXACT MATCH':<14}"
for lbl in col_labels:
    score = f"{exact_counts[lbl]}/{puzzle_count}"
    color = GREEN if exact_counts[lbl] == puzzle_count else (YELLOW if exact_counts[lbl] > 0 else RED)
    tot += f"  {color}{score:<18}{RESET}"
print(f"{BOLD}{tot}{RESET}")

# ── Detailed diffs for any failures ──────────────────────────────────────────
any_wrong = False
for puzzle in PUZZLES:
    name = puzzle["name"]
    expected = puzzle.get("expected_grid")
    if not expected:
        continue
    for lbl in col_labels:
        r = results[name].get(lbl, {})
        if r.get("wrong"):
            if not any_wrong:
                print(f"\n{BOLD}{CYAN}{'='*72}{RESET}")
                print(f"{BOLD}  Wrong cells detail{RESET}")
                print(f"{BOLD}{CYAN}{'='*72}{RESET}")
            any_wrong = True
            print(f"\n  {BOLD}{name} / {lbl}{RESET} — {len(r['wrong'])} wrong cell(s):")
            for (row, col, exp, act) in r["wrong"]:
                print(f"    [{row}][{col}]: expected {exp}, got {act}")

print(f"\n{BOLD}{CYAN}{'='*72}{RESET}\n")
PYEOF
)
