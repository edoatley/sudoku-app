# Fix/replace the Terraform state visualiser script

**Summary:** `scripts/terraform-utils/visualise.sh` is meant to let the user pick a Terraform workspace and see its resources (list, names, relationships) pulled from the S3-backed state, but it depends on Docker + the `im2nguyen/rover` image — not self-contained, and the user wants an alternative.

**Branch context:** `rc-terraform-review` — this came up after a large infra-review remediation (PR #116) where the user, while working with live Terraform state across multiple workspaces (`default`, `rc-shared`, `rc-terraform-review`, and an orphaned `rc-test-cicd` that had to be found and torn down), asked for a way to visualise state contents.

## Why deferred

Explicitly deferred by the user via `/todo` — this is a separate, standalone tool fix, not part of the infra-review PR. No urgency stated; just wanted it captured for a future session.

## Context

**Relevant files:**
- `scripts/terraform-utils/visualise.sh` — the script to fix/replace (currently untracked, not yet committed to the repo)
- `infra/terraform.tf` — defines the S3 backend (`bucket = "sudoku-tf-state"`, `key = "sudoku/terraform.tfstate"`, region `eu-west-2`) that the script must match
- `infra/main.tf` — `local.is_default`/`local.is_rc`/`local.suffix` show the workspace naming convention (`default`, `rc-shared`, `rc-*`) the script needs to enumerate
- `scripts/README.md` — where other `scripts/` tools are documented; this script isn't listed there yet

**Current state:**
The script lists workspaces by doing an `aws s3 ls` under the state bucket's `env:/` prefix (correctly handling that the `default` workspace's state lives at the bare key, not under `env:/`), lets the user `select` one interactively, downloads that workspace's `terraform.tfstate` from S3, copies it into the current working directory as `./terraform.tfstate`, then runs `docker run ... im2nguyen/rover:latest -workingDir /src` to serve a web UI on port 8080. Known problems:
1. Requires Docker + pulling `im2nguyen/rover` from a registry — not self-contained, and the user said an alternative tool is fine.
2. Copies state to `$(pwd)/terraform.tfstate` with no cleanup trap for that specific file (only the temp dir under `$LOCAL_STATE_DIR` is cleaned up on exit) — if run from `infra/`, leaves a stray file behind.
3. Rover typically wants the matching `.tf` config alongside the state for a full picture; the script only copies the bare state file into `/src`, so the visualisation may be incomplete without also copying (or symlinking) the `infra/*.tf` files.
4. No error handling on `aws s3 cp`/`aws s3 ls` failures; the `select` loop assumes an interactive terminal.

**Key constraints:**
- Must work against the real S3 backend (`sudoku-tf-state` bucket, `eu-west-2`), using whatever AWS profile/credentials the user has configured (repo convention elsewhere is `AWS_PROFILE=sandbox`, see `scripts/infra/deploy-local.sh` for the pattern).
- Must handle the `default` workspace's state living at a different S3 key than named workspaces (already handled correctly — preserve this logic).
- No test/CI coverage exists for this script; it's a personal dev tool, not gated by `scripts/local/local-alltests.sh`.

## What to do

1. Ask the user (or decide) on the replacement approach — options, roughly in order of "self-contained-ness":
   - `inframap` (single static Go binary, no Docker) — converts `terraform show -json` state output into a `.dot` graph showing resources and relationships, can render to PNG/SVG with `dot` (from `graphviz`, likely already available or trivially installable via brew).
   - Plain `terraform show -json terraform.tfstate | jq` — zero extra tools beyond `terraform` + `jq` (both already used elsewhere in this repo's scripts), gives a structured resource list/attributes but no visual relationship graph.
   - Keep Rover but fix the script's bugs (add config files to `/src`, fix cleanup, add error handling) if the user actually likes the Rover UI and Docker isn't a real objection.
2. Whichever approach: keep the existing workspace-listing/selection logic (lines 12–35) — it's correct.
3. Fix resource cleanup — anything written outside `$LOCAL_STATE_DIR` (e.g. a copied `terraform.tfstate` or a rendered graph file) needs its own explicit cleanup or should be written inside the already-cleaned-up temp dir instead.
4. Add basic error handling: check `aws s3 cp` exit status before proceeding; fail with a clear message if empty/missing.
5. Once working, add a short section to `scripts/README.md` documenting usage (this script currently isn't mentioned there).

## Acceptance criteria

- [ ] Running the script against a real workspace (e.g. `rc-shared`) shows a resource list with names, without requiring Docker (unless Rover is deliberately kept)
- [ ] Relationships between resources (e.g. which IAM role a policy attaches to) are visible, not just a flat list
- [ ] No stray files left in the working directory after the script exits
- [ ] Documented in `scripts/README.md`

## Related specs / docs

None — this is a personal dev tool with no EARS spec or LLD coverage.
