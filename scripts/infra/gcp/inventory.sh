#!/usr/bin/env bash
# Summarise what the GCP project contains, and how that compares with what Pulumi manages.
#
# Two questions, two different sources:
#   * `pulumi stack export`          — what Pulumi MANAGES
#   * `gcloud projects get-iam-policy` — what the project ACTUALLY has
#
# The gap between them is drift. It exists by design: every IAM grant is additive
# (gcp.projects.IAMMember), never authoritative, so Pulumi neither sees nor removes bindings it
# did not create. That is deliberate — an authoritative policy would delete the operator's own
# Owner grant and Google's per-API service agents, on a project with no organization to restore
# from — but it means nothing reports a hand-granted role. This script is that report.
#
# First concrete step toward docs/todo/gcp-iam-drift-detection.md. Read-only; creates nothing.
#
# Usage: scripts/infra/gcp/inventory.sh [--iam-only]
# @spec CP-PUL-012, CP-PUL-013
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
STACK_DIR="${REPO_ROOT}/infra/gcp/bootstrap"
IAM_ONLY=false
[[ "${1:-}" == "--iam-only" ]] && IAM_ONLY=true

BOLD=$'\033[1m'; DIM=$'\033[2m'; RED=$'\033[0;31m'; GREEN=$'\033[0;32m'
YELLOW=$'\033[1;33m'; CYAN=$'\033[0;36m'; RESET=$'\033[0m'

command -v pulumi >/dev/null || { echo "pulumi not found" >&2; exit 1; }
command -v gcloud >/dev/null || { echo "gcloud not found" >&2; exit 1; }

cd "${STACK_DIR}"
STATE="$(mktemp)"; trap 'rm -f "${STATE}"' EXIT
pulumi stack export >"${STATE}" 2>/dev/null
PROJECT_ID="$(pulumi stack output project_id 2>/dev/null)"
[[ -n "${PROJECT_ID}" ]] || { echo "could not read project_id from the stack" >&2; exit 1; }

echo "${BOLD}${CYAN}=== GCP inventory — ${PROJECT_ID} ===${RESET}"
echo

if [[ "${IAM_ONLY}" == "false" ]]; then
  echo "${BOLD}Pulumi-managed resources${RESET}"
  python3 - "${STATE}" <<'PY'
import json, sys, collections
rs = json.load(open(sys.argv[1]))["deployment"]["resources"]
real = [r for r in rs if r.get("custom") and not r["type"].startswith("pulumi:")]
groups = collections.defaultdict(list)
for r in real:
    o = r.get("outputs") or {}
    label = o.get("name") or o.get("email") or o.get("repositoryId") or r.get("id", "")
    groups[r["type"].split(":")[-1]].append(str(label))
print(f"  {len(real)} cloud resources (+{len(rs) - len(real)} Pulumi components/providers)\n")
for kind in sorted(groups):
    items = sorted(groups[kind])
    if kind == "Service":
        names = ", ".join(i.split("/")[-1].replace(".googleapis.com", "") for i in items)
        print(f"  {kind} ({len(items)}): {names}")
    else:
        print(f"  {kind} ({len(items)})")
        for i in items:
            short = i.rsplit("/", 1)[-1] if len(i) > 80 else i
            print(f"      {short}")
PY
  echo
fi

echo "${BOLD}Project IAM — managed vs actual${RESET}"
ACTUAL="$(mktemp)"; trap 'rm -f "${STATE}" "${ACTUAL}"' EXIT
gcloud projects get-iam-policy "${PROJECT_ID}" --format=json >"${ACTUAL}" 2>/dev/null

python3 - "${STATE}" "${ACTUAL}" "${GREEN}" "${YELLOW}" "${RED}" "${DIM}" "${RESET}" <<'PY'
import json, sys
state, actual, GREEN, YELLOW, RED, DIM, RESET = sys.argv[1:8]
rs = json.load(open(state))["deployment"]["resources"]

managed = set()
for r in rs:
    # NB: pulumi-gcp spells it "iAMMember" (capital A). Match by suffix, not an exact string —
    # an exact match silently found nothing and reported every binding as unmanaged.
    if r["type"].lower().endswith("/iammember:iammember") and "projects/" in r["type"]:
        o = r.get("outputs") or {}
        if o.get("role") and o.get("member"):
            managed.add((o["role"], o["member"]))

live = set()
for b in json.load(open(actual)).get("bindings", []):
    for m in b.get("members", []):
        live.add((b["role"], m))

def agent(m):
    """A Google-created per-API service agent. Expected, and must never be removed — deleting
    these breaks the service that owns them."""
    return ".gserviceaccount.com" in m and (
        "@gcp-sa-" in m or m.startswith("serviceAccount:service-")
        or "@containerregistry." in m or "@serverless-robot-prod." in m
    )


def default_compute(m):
    """The default compute service account. Created by Google with roles/editor, used by nothing
    here — Cloud Run runs as sudoku-run. Broad standing privilege on an unused identity."""
    return "-compute@developer.gserviceaccount.com" in m

unmanaged = sorted(live - managed)
missing = sorted(managed - live)

print(f"  {GREEN}managed and present{RESET}: {len(managed & live)}")
if missing:
    print(f"  {RED}managed but MISSING from the project{RESET}: {len(missing)}  <- removed out-of-band")
    for role, m in missing:
        print(f"      {role}  {m}")

agents = [x for x in unmanaged if agent(x[1])]
defaults = [x for x in unmanaged if default_compute(x[1])]
humans = [x for x in unmanaged if not agent(x[1]) and not default_compute(x[1])]
print(f"  {DIM}unmanaged, Google service agents (expected){RESET}: {len(agents)}")
for role, m in agents:
    print(f"      {DIM}{role}  {m.split(':')[-1]}{RESET}")
if defaults:
    print(f"  {YELLOW}unmanaged, default compute service account — used by nothing here{RESET}: {len(defaults)}")
    for role, m in defaults:
        print(f"      {YELLOW}{role}  {m.split(':')[-1]}{RESET}")
if humans:
    print(f"  {YELLOW}unmanaged, neither agent nor default — review these{RESET}: {len(humans)}")
    for role, m in humans:
        print(f"      {YELLOW}{role}  {m}{RESET}")
print()
print(f"  {DIM}Unmanaged bindings are not necessarily wrong — the operator's own Owner grant")
print(f"  belongs here. They are simply invisible to `pulumi preview`, which is the point.{RESET}")
PY
