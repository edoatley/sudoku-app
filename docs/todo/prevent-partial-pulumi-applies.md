# Prevent partial Pulumi applies from cancelled CI runs

**Summary:** A cancelled `pulumi up` leaves a held lock in the GCS state bucket and a
half-applied stack; nothing in CI currently prevents cancellation mid-apply or recovers from it.

**Branch context:** `plan/gcp-pulumi-phase-7` — observed while planning the unified deploy
workflow, which routes GCP deploys through a caller that sets `cancel-in-progress: true`.

## Why deferred

Phase 7 will prove whether a called workflow inherits the caller's cancellation semantics, and
fix that one case if it does. This todo is the broader problem: cancellation can also come from a
human pressing cancel, a job timeout, or a runner dying, and none of those are addressed by
concurrency settings.

## Context

**Relevant files:**
- `.github/workflows/deploy-gcp-pulumi.yml` — the `Pulumi up` step; declares its own
  `concurrency` with `cancel-in-progress: false`
- `.github/workflows/ci-deploy.yml` — sets `cancel-in-progress: true` deliberately, so Terraform
  state locks do not pile up across concurrent runs
- `.github/workflows/teardown-gcp-pulumi.yml` — same exposure on `pulumi destroy`
- `docs/planning/gcp-pulumi-phase-7-unified-deploy.md` §5 D3 — where the narrow case is handled

**Current state:**
`pulumi up` takes a lock in `gs://sudoku-pulumi-state-eo` for the duration of an update. If the
process is killed, the lock file remains and every later run on that stack fails with a
"conflicting update" error until someone runs `pulumi cancel --stack <stack>` by hand. The stack
itself is left in whatever partial state the apply reached, which for the app stack can mean
Cloud Run services existing while the Hosting site does not. Recovery is manual and there is no
runbook entry for it.

**Key constraints:**
- `pulumi cancel` only clears the lock; it does not roll the stack back. A `pulumi up` re-run is
  what converges it, and a re-run needs the lock gone first.
- `--force` is prohibited repo-wide: `pulumi stack rm --force` orphans live resources.
- Ephemeral `rcg-*` stacks are cheap to destroy and recreate; the `prod` app and bootstrap stacks
  are not, and are the ones where a held lock actually hurts.

## What to do

1. Decide the policy: either (a) never cancel a GCP deploy — drop `cancel-in-progress` for
   GCP-bound runs and let them queue, or (b) allow cancellation and add automatic recovery.
2. For (b), add a step that runs on cancellation (`if: cancelled()`) invoking
   `pulumi cancel --yes --stack "$STACK"`, so the lock is released by the run that took it.
   Note this does *not* make the stack consistent — only re-runnable.
3. Add a runbook entry to `docs/runbooks/gcp-manual-setup.md` covering the symptom ("conflicting
   update" / "the stack is currently locked"), `pulumi cancel`, and the fact that a re-run is what
   converges a partially-applied stack.
4. Consider guarding the `prod` app stack specifically: a held lock there blocks production
   deploys, and it is the one stack where queuing is clearly preferable to cancelling.

## Acceptance criteria

- [ ] Cancelling a GCP deploy mid-`pulumi up` leaves no lock that a subsequent run cannot clear
      without manual intervention — demonstrated on an `rcg-*` stack, not argued
- [ ] A re-run after a cancelled apply converges the stack with no manual step
- [ ] The symptom and its recovery are documented in the GCP runbook
- [ ] `--force` appears nowhere in the recovery path

## Related specs / docs

- [`docs/llds/cloud-platform-gcp.md`](../llds/cloud-platform-gcp.md) — Stack Strategy (the
  `--force` prohibition and the ephemeral lifecycle)
- [`docs/planning/gcp-pulumi-phase-7-unified-deploy.md`](../planning/gcp-pulumi-phase-7-unified-deploy.md)
  — §5 D3, the narrow concurrency case Phase 7 handles
