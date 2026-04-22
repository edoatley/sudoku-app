#!/usr/bin/env bash
# test-recognition-real-bedrock.sh — Run image recognition tests against real Bedrock (sandbox profile).
#
# Usage:
#   bash scripts/local/test-recognition-real-bedrock.sh [--mode accuracy|compare]
#
# Modes:
#   accuracy  (default) Run pytest e2e suite including exact grid match for all fixtures in
#                       tests/e2e_config.json. Fast — one Bedrock call per puzzle.
#   compare             Run every model × PIL combination against all ground-truth fixtures
#                       and print an accuracy matrix. Slow — many Bedrock calls.
#
# Requires AWS SSO login:
#   aws sso login --profile sandbox

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IR_DIR="${REPO_ROOT}/image_recognition"
VENV="${IR_DIR}/.venv"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

# ── Parse arguments ───────────────────────────────────────────────────────────
MODE="accuracy"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      MODE="${2:?--mode requires a value: accuracy|compare}"
      shift 2
      ;;
    *)
      echo -e "${RED}Unknown argument: $1${RESET}" >&2
      echo "Usage: $0 [--mode accuracy|compare]" >&2
      exit 1
      ;;
  esac
done

if [[ "${MODE}" != "accuracy" && "${MODE}" != "compare" ]]; then
  echo -e "${RED}Invalid mode '${MODE}' — must be 'accuracy' or 'compare'${RESET}" >&2
  exit 1
fi

# ── AWS credentials check ─────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}=== Checking AWS credentials (sandbox profile) ===${RESET}"
if ! AWS_PROFILE=sandbox aws sts get-caller-identity --output text &>/dev/null; then
  echo -e "${RED}✗ AWS sandbox profile not authenticated.${RESET}"
  echo -e "  Run: ${BOLD}aws sso login --profile sandbox${RESET}"
  exit 1
fi
echo -e "${GREEN}✓ Authenticated${RESET}"

# ── Python venv ───────────────────────────────────────────────────────────────
if [[ ! -f "${VENV}/bin/python" ]]; then
  echo -e "\n${YELLOW}Setting up .venv...${RESET}"
  python3 -m venv "${VENV}"
  "${VENV}/bin/pip" install -q -r "${IR_DIR}/requirements-dev.txt"
fi

# ── Mode: accuracy ────────────────────────────────────────────────────────────
if [[ "${MODE}" == "accuracy" ]]; then
  echo -e "\n${BOLD}${CYAN}=== Running accuracy tests (pytest e2e) ===${RESET}"
  echo -e "  Model: Haiku 4.5 | PIL: disabled | Fixtures: tests/e2e_config.json\n"

  (
    cd "${IR_DIR}"
    source "${VENV}/bin/activate"
    AWS_PROFILE=sandbox AWS_REGION_NAME=eu-west-2 \
      python -m pytest tests/test_e2e_bedrock.py -v -m e2e -s
  )
  exit 0
fi

# ── Mode: compare ─────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}=== Running model comparison matrix ===${RESET}\n"

(
  cd "${IR_DIR}"
  source "${VENV}/bin/activate"
  AWS_PROFILE=sandbox AWS_REGION_NAME=eu-west-2 python3 - <<'PYEOF'
import json, sys, logging
from pathlib import Path

logging.getLogger("handler").setLevel(logging.WARNING)

sys.path.insert(0, ".")
import boto3
import handler as h

CONFIG = json.loads(Path("tests/e2e_config.json").read_text())
PUZZLES = CONFIG["puzzles"]

MODELS = [
    ("haiku-4.5", "eu.anthropic.claude-haiku-4-5-20251001-v1:0"),
    ("nemotron",  "nvidia.nemotron-nano-12b-v2"),
]
PIL_MODES = [("no-PIL", False), ("PIL", True)]

client = boto3.client("bedrock-runtime", region_name="eu-west-2")

GREEN  = '\033[0;32m'
RED    = '\033[0;31m'
YELLOW = '\033[1;33m'
RESET  = '\033[0m'
BOLD   = '\033[1m'
CYAN   = '\033[0;36m'


def cell_accuracy(actual, expected):
    correct, total, wrong = 0, 0, []
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
    h._MODELS = [model_id]
    processed = h._downscale_image(image_bytes) if pil_enabled else image_bytes
    try:
        grid, valid, _ = h._recognize_with_bedrock(client, processed)
        return grid, valid, None
    except Exception as e:
        return None, False, str(e)


col_labels = [f"{name}+{pil}" for name, _ in MODELS for pil, _ in PIL_MODES]
results = {}

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
                    "exact": len(wrong) == 0,
                }
                status = (f"{GREEN}✓ {correct}/{total}{RESET}" if not wrong
                          else f"{RED}✗ {correct}/{total} ({len(wrong)} errors){RESET}")
                print(status)

# ── Summary matrix ────────────────────────────────────────────────────────────
print(f"\n{BOLD}{CYAN}{'='*72}{RESET}")
print(f"{BOLD}  Accuracy Matrix (correct clue cells / total clue cells){RESET}")
print(f"{BOLD}{CYAN}{'='*72}{RESET}")

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
            row += f"  {RED}{'FAIL':<18}{RESET}"
        elif "no_truth" in r:
            row += f"  {YELLOW}{'(no truth)':<18}{RESET}"
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
tot = f"{'EXACT MATCH':<14}"
for lbl in col_labels:
    score = f"{exact_counts[lbl]}/{puzzle_count}"
    color = GREEN if exact_counts[lbl] == puzzle_count else (YELLOW if exact_counts[lbl] > 0 else RED)
    tot += f"  {color}{score:<18}{RESET}"
print(f"{BOLD}{tot}{RESET}")

# ── Detailed diffs ────────────────────────────────────────────────────────────
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
