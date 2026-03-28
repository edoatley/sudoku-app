#!/usr/bin/env bash
# test_puzzle.sh — encode puzzle_1.jpeg as base64 and call handler.py directly
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="$SCRIPT_DIR/.venv"
FIXTURE="$SCRIPT_DIR/tests/fixtures/puzzle_1.jpeg"
TMP_PAYLOAD=$(mktemp)

cleanup() { rm -f "$TMP_PAYLOAD"; }
trap cleanup EXIT

# ── 1. Create venv if needed ──────────────────────────────────────────────────
if [[ ! -d "$VENV_DIR" ]]; then
  echo "Creating virtual environment..."
  python3 -m venv "$VENV_DIR"
fi

# ── 2. Install dependencies ───────────────────────────────────────────────────
echo "Installing dependencies..."
"$VENV_DIR/bin/pip" install --quiet -r "$SCRIPT_DIR/requirements.txt"

# ── 3. Build the Lambda event payload and write to temp file ──────────────────
echo "Encoding $FIXTURE..."
"$VENV_DIR/bin/python" - "$FIXTURE" > "$TMP_PAYLOAD" <<'PYEOF'
import base64, json, sys

image_path = sys.argv[1]
with open(image_path, "rb") as f:
    b64 = base64.b64encode(f.read()).decode()

event = {"body": json.dumps({"image": b64})}
print(json.dumps(event))
PYEOF

# ── 4. Invoke handler.py ──────────────────────────────────────────────────────
echo "Calling handler..."
export AWS_REGION_NAME="us-east-1"
export AWS_PROFILE="sandbox"
RESULT=$(cd "$SCRIPT_DIR" && "$VENV_DIR/bin/python" - "$TMP_PAYLOAD" <<'PYEOF'
import json, sys, os
sys.path.insert(0, os.getcwd())

with open(sys.argv[1]) as f:
    event = json.load(f)

from handler import handler
response = handler(event, None)
print(json.dumps(response, indent=2))
PYEOF
)

echo ""
echo "=== Raw response ==="
echo "$RESULT"

# ── 5. Pretty-print the grid if successful ────────────────────────────────────
echo ""
echo "=== Parsed grid ==="
"$VENV_DIR/bin/python" - <<PYEOF
import json

response = json.loads(r"""$RESULT""")
status = response.get("statusCode")
body = json.loads(response.get("body", "{}"))

if status == 200:
    grid = body.get("originalGrid", [])
    print(f"Status: {status} OK")
    print()
    for row in grid:
        print(" ".join(str(n) if n != 0 else "." for n in row))
else:
    print(f"Status: {status}")
    print(f"Error:  {body.get('error', 'unknown')}")
PYEOF

# -- 6. Check answer is correct
# 1. Extract the inner stringified body from the Lambda response, parse it, and grab just the originalGrid array. 
# The '-c' flag makes it "compact" (one line, no spaces) so bash can compare it easily.
actual_grid=$(echo "$RESULT" | jq -r '.body' | jq -c '.originalGrid')

# 2. Use 'cat' with a here-doc, and format it compactly to match
expected_grid=$(cat << 'EOF' | jq -c '.originalGrid'
{
  "originalGrid": [
    [0, 0, 0, 0, 0, 0, 4, 0, 0],
    [2, 0, 0, 1, 0, 0, 5, 9, 0],
    [0, 0, 0, 7, 0, 3, 0, 0, 8],
    [6, 9, 0, 0, 0, 0, 0, 0, 7],
    [0, 8, 0, 0, 0, 0, 0, 6, 0],
    [0, 1, 0, 0, 0, 0, 9, 3, 0],
    [0, 0, 0, 5, 4, 0, 0, 0, 1],
    [0, 0, 0, 0, 2, 9, 3, 0, 0],
    [0, 0, 0, 0, 6, 0, 0, 0, 0]
  ]
}
EOF
)

# 3. Compare the two compact JSON strings
# ... (Keep Step 1 and 2 where you extract actual_grid and expected_grid) ...

# 3. Compare the two compact JSON strings and highlight diffs
if [[ "$actual_grid" == "$expected_grid" ]]; then
    echo "✅ SUCCESS: The extracted grid matches the expected grid perfectly!"
else
    echo "❌ FAILURE: The grids do not match."
    echo ""
    
    # Export variables so Python can read them safely without quote-escaping issues
    export EXPECTED_GRID="$expected_grid"
    export ACTUAL_GRID="$actual_grid"
    
    "$VENV_DIR/bin/python" - << 'PYEOF'
import os, json

expected = json.loads(os.environ.get("EXPECTED_GRID", "[]"))
actual = json.loads(os.environ.get("ACTUAL_GRID", "[]"))

RED = '\033[91m'
GREEN = '\033[92m'
RESET = '\033[0m'

def format_grid(primary_grid, reference_grid, diff_color):
    """Formats the grid into a single string, coloring values that differ."""
    grid_str = ""
    for r_idx, p_row in enumerate(primary_grid):
        r_row = reference_grid[r_idx] if r_idx < len(reference_grid) else []
        row_str = []
        for c_idx, p_val in enumerate(p_row):
            r_val = r_row[c_idx] if c_idx < len(r_row) else None
            # If values don't match, paint it!
            if p_val != r_val:
                row_str.append(f"{diff_color}{p_val}{RESET}")
            else:
                row_str.append(str(p_val))
        grid_str += "[" + ",".join(row_str) + "],"
    return f"[{grid_str.rstrip(',')}]"

print(f"Expected: {format_grid(expected, actual, GREEN)}")
print(f"Actual:   {format_grid(actual, expected, RED)}")
PYEOF
    echo ""
    exit 1
fi
