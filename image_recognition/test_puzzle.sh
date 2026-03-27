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
