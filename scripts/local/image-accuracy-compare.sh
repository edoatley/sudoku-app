#!/usr/bin/env bash
# Compare vision providers on recognition accuracy, N runs each.
#
# The decision tool for flipping IMAGE_AI_PROVIDER to vertex. Mirrors coach-quality-repeat.sh:
# credential-bearing, costs money per run, and deliberately outside CI and the pre-push suite.
#
# Needs AWS credentials for bedrock, and ADC + GCP_PROJECT_ID for vertex.
#
# Usage: scripts/local/image-accuracy-compare.sh [runs] [provider ...]
# @spec IR-TEST-003
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IR_DIR="${REPO_ROOT}/image_recognition"
RUNS="${1:-3}"
shift || true
PROVIDERS=("${@:-bedrock vertex}")
read -ra PROVIDERS <<<"${PROVIDERS[*]}"

BOLD=$'\033[1m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[1;33m'; RESET=$'\033[0m'

cd "${IR_DIR}"
[[ -x .venv/bin/python ]] || { echo "run local-alltests.sh once to create .venv" >&2; exit 1; }

echo "${BOLD}Recognition accuracy — ${RUNS} run(s) per provider${RESET}"
echo

for provider in "${PROVIDERS[@]}"; do
  echo "${BOLD}=== ${provider} ===${RESET}"
  for run in $(seq 1 "${RUNS}"); do
    echo "  run ${run}/${RUNS}"
    IMAGE_AI_PROVIDER="${provider}" .venv/bin/python -m pytest tests/test_accuracy.py \
      -m accuracy -q -k "report" 2>&1 | grep -E "provider:|mean cell|exact grids|valid puzzles|^    " || true
  done
  echo
done

echo "${BOLD}Aggregate across runs${RESET}"
.venv/bin/python - <<'PY'
import json, pathlib, collections
reports = sorted((pathlib.Path("tests/reports")).glob("*.json"))
by = collections.defaultdict(list)
for f in reports:
    d = json.loads(f.read_text())
    by[d["provider"]].append(d)
if not by:
    print("  no reports found")
else:
    print(f"  {'provider':10} {'runs':>5} {'mean cell acc':>15} {'exact grids':>13} {'valid':>7}")
    for p, runs in sorted(by.items()):
        n = len(runs)
        acc = sum(r["mean_cell_accuracy"] for r in runs) / n
        ex = sum(r["exact_grids"] for r in runs) / n
        va = sum(r["valid_puzzles"] for r in runs) / n
        total = runs[0]["total"]
        print(f"  {p:10} {n:>5} {acc:>14.1%} {ex:>8.1f}/{total} {va:>4.1f}/{total}")
    print()
    print("  A narrow win is a tie — five fixtures cannot separate close results.")
    print("  Prefer the incumbent unless the margin is clear.")
PY
