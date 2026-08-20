# Review Terraform CI/testing strategy and close the infra/gcp coverage gap

**Summary:** Audit the current Terraform validation approach (fmt + validate only, no
`terraform test`, no tflint, inconsistent AWS/GCP coverage between local and CI) against best
practice, and decide whether to add real Terraform testing.

**Branch context:** `feat/gcp-coach-vertex-rollout-var` — surfaced while adding a Terraform
variable for the Vertex AI coach rollout (PR #185) and separately verifying its own PR checks.

## Why deferred

Found by inspection while confirming PR #185's checks passed cleanly — not something that PR
needed to fix, and warrants its own look at options (tflint, checkov, `terraform test`) rather
than a reactive patch.

## Context

**Relevant files:**
- `scripts/local/local-alltests.sh` (~line 325-352, "Suite 7: Infra (Terraform)") — the local
  pre-push suite. Only runs `terraform fmt -check -recursive` + `terraform init -backend=false` +
  `terraform validate` against `infra/aws`. **Never touches `infra/gcp`.**
- `.github/workflows/ci.yml` (~line 130-144, job `ci-infra`) — CI runs the equivalent check as a
  matrix over `[infra/aws, infra/gcp]` via the composite action below. So CI covers both clouds;
  the local script only covers one.
- `.github/actions/terraform-validate/action.yml` — the reusable composite action: `terraform fmt
  -check -recursive`, `terraform init -backend=false`, `terraform validate`. No plan, no test, no
  linter.
- `.pre-commit-config.yaml` (~line 50-61) — `trivy-config` hook runs `trivy config infra/aws/`
  only (same AWS-only gap as the local script); `trivy-fs` scans the whole repo for dependency
  vulnerabilities, not Terraform-specific.
- `infra/aws/*.tf`, `infra/gcp/*.tf` — several files have `# checkov:skip=...` comments (e.g.
  `infra/gcp/cloud_run.tf`), but no checkov step exists in CI or pre-commit today — worth
  confirming whether Trivy's config scan honors those skip IDs or whether they're vestigial.

**Current state:**
Terraform "testing" today means syntax/formatting validation only (`fmt` + `validate`), never
`terraform plan`, never a policy/lint tool beyond Trivy's IaC config scan (AWS-only locally, not
run at all for GCP outside CI), and no `.tftest.hcl` test files exist anywhere in the repo (
confirmed via `find infra -iname "*.tftest.hcl"` — zero results). CI is more complete than local
tooling: `ci.yml`'s `ci-infra` job matrixes over both `infra/aws` and `infra/gcp`, but
`local-alltests.sh` and the pre-commit Trivy hook only ever check `infra/aws`. This means a
GCP-only Terraform change (like PR #185) never gets a local Trivy/fmt/validate signal before
push — CI is the first checkpoint, which the CLAUDE.md pre-push mandate explicitly says CI should
not be relied on for.

**Key constraints:**
- CLAUDE.md's Pre-Push Testing section mandates `scripts/local/local-alltests.sh` before every
  push and explicitly says CI is not a substitute — the current AWS-only infra suite violates the
  spirit of that rule for GCP changes.
- `docs/llds/cloud-platform.md` documents both `infra/aws/` and `infra/gcp/` as first-class,
  parity-maintained facets (see "GCP Design Decisions" / "AWS facet" / "GCP facet" reference
  lists) — the tooling asymmetry isn't reflected in any design doc, so it reads as an oversight,
  not a deliberate choice.
- Any change here is tooling/process, not application behavior — likely doesn't need EARS specs,
  but check `docs/arrows/testing-strategy.md` for whether Terraform validation is already
  spec-tracked there before assuming it's exempt.

## What to do

1. Read `docs/arrows/testing-strategy.md` to see whether Terraform CI is already spec-tracked;
   if not, decide whether this warrants a spec or is purely process/tooling (per that arrow's own
   framing — it notes some content is "process/architecture, not spec-tracked").
2. Fix the immediate asymmetry: extend `scripts/local/local-alltests.sh`'s infra suite to loop
   over both `infra/aws` and `infra/gcp` (mirror the `ci-infra` matrix), and extend the
   `trivy-config` pre-commit hook similarly (either a second hook entry or loop both dirs).
3. Evaluate `terraform test` (native HCL test framework, `.tftest.hcl` files, `terraform test`
   command) for the highest-value modules — likely `infra/gcp/variables.tf` validation blocks
   (e.g. the `coach_ai_provider` allowed-values check added in PR #185) and any conditional
   resource logic (the `enable_coach`/`coach_ai_provider` dynamic blocks in `cloud_run.tf` are a
   good first candidate — assert the AWS secret env vars are absent when `coach_ai_provider =
   "vertex"`).
4. Evaluate tflint (style/correctness linting beyond `fmt`) and whether checkov should actually
   run somewhere given the existing `checkov:skip` comments, or whether those comments should be
   removed as dead convention if Trivy's config scan is the only scanner in use.
5. Decide scope: is a full `terraform plan` in CI (against a throwaway/sandboxed state) worth the
   complexity for a two-cloud personal project, or does fmt+validate+test remain sufficient once
   the AWS/GCP asymmetry above is fixed?

## Acceptance criteria

- [ ] `scripts/local/local-alltests.sh` validates both `infra/aws` and `infra/gcp` (or explicitly
      documents why not, if the decision is to leave it AWS-only)
- [ ] Pre-commit Trivy config scan covers both cloud directories, or the gap is a documented,
      deliberate decision
- [ ] A decision is recorded (LLD row or decision doc) on whether `terraform test` / tflint /
      checkov are adopted, with rationale — not just left unexamined
- [ ] If `.tftest.hcl` tests are added, they run in both `local-alltests.sh` and CI

## Related specs / docs

- [`docs/arrows/testing-strategy.md`](../arrows/testing-strategy.md) — cross-cutting test
  pyramid/CI structure; check whether Terraform validation belongs here
- [`docs/llds/cloud-platform.md`](../llds/cloud-platform.md) — GCP/AWS facet parity design,
  "GCP Design Decisions" and "Technical Debt & Inconsistencies" sections are the natural home for
  whatever gets decided
