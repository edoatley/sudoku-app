# GCP IAM drift detection

**Summary:** Pulumi grants every GCP IAM binding additively, so it neither sees nor removes a role granted by hand — decide how that drift gets detected.

**Branch context:** `gcp-pulumi-phase-1` — the Pulumi component library and its build-time guardrail against authoritative IAM resources.

## Why deferred

Surfaced while explaining the additive-only IAM rule during review of PR #223. The rule itself is settled and enforced; what is *not* settled is the gap it deliberately leaves open. Choosing a detection strategy needs its own thought and probably its own small PR — it is not Phase 1 scaffolding, and it should not hold up the bootstrap stack.

## Context

**Relevant files:**
- `infra/gcp/components/service_identity.py` — the only place project-level roles are granted; uses `gcp.projects.IAMMember`
- `infra/gcp/components/container_service.py` — grants `roles/run.invoker` to `allUsers` via `gcp.cloudrunv2.ServiceIamMember`
- `infra/gcp/tests/test_no_authoritative_iam.py` — AST check failing the build if `IAMBinding`/`IAMPolicy` (either casing) appears
- `docs/planning/gcp-pulumi-phase-2-bootstrap.md` §4 — the authoritative list of service accounts and their intended roles
- `docs/llds/cloud-platform-gcp.md` — "IAM: additive only", and the two-stack privilege split

**Current state:**
Every binding is additive: `IAMMember` grants exactly one `(role, member)` pair and leaves everything else untouched. This is deliberate — an authoritative project policy would delete the operator's own `roles/owner` grant and the service agents Google auto-creates for each enabled API, and this account has **no organization** (`gcloud organizations list` returns zero), so there is no admin above the project to restore access. The cost of that safety is that Pulumi has no opinion about bindings it did not create. A role granted by hand in the console is invisible to `pulumi preview`, survives every `pulumi up`, and will never be reported. `scripts/infra/gcp/inventory.sh` (added 2026-09-13, `make gcp-inventory`) now *reports* the gap:
it diffs Pulumi-managed bindings against the live policy and classifies what is left over into
Google service agents (expected), the default compute service account, and everything else. What
is still missing is a **scheduled** run and a decision about whether drift should fail anything.

**Key constraints:**
- `CP-PUL-012` — the CI deploy SA must hold no IAM-admin, WIF-admin, Secret Manager or billing permission. Any detection job must not need permissions that violate this; `roles/iam.securityReviewer` is read-only and sufficient.
- `CP-PUL-013` — authoritative IAM resources stay banned. Remediation must not be "switch to `IAMPolicy`".
- Org policy constraints are unavailable (no organization).
- Google's service agents legitimately add bindings. Any allowlist must tolerate `service-<project-number>@gcp-sa-*.iam.gserviceaccount.com` members, or it will be noise on every run.

## What to do

1. Decide the strategy. The reporting half exists (`scripts/infra/gcp/inventory.sh`); what remains is scheduling it and deciding the consequence. Leading option: run it from a scheduled GitHub Actions job. Alternatives: Cloud Asset Inventory feeds with a Pub/Sub sink (richer, real-time, more moving parts), or accepting the gap with a documented quarterly manual audit.
2. The allowlist is already derived rather than hand-maintained — `pulumi stack export` contains every binding Pulumi created, so drift is "live policy minus Pulumi-known minus service-agent pattern". Keep it that way; a hand-maintained list would rot.
3. Decide whether drift fails the job or only reports. Recommend report-only first: a hand-granted role is often a deliberate debugging step, and a red build on someone else's console click is noise until the baseline is proven quiet.
4. Wire it into the existing `ci-pulumi` job or a new scheduled workflow. It needs cloud credentials, so it cannot live in `ci-pulumi` as that job is credential-free by design (`CP-PUL-081`).
5. Add an EARS spec for whatever is chosen, in the `CP-PUL-01x` block alongside the other privilege specs, and record the decision in the LLD's "IAM: additive only" section.

## Acceptance criteria

- [ ] A decision is recorded in `docs/llds/cloud-platform-gcp.md` with its rationale, including if the decision is to accept the gap
- [ ] If automated: a hand-granted role on the live project is detected within one scheduled run, and the run is quiet (no findings) when no drift exists
- [ ] Google service-agent bindings do not appear as drift
- [ ] The detection identity holds only read-only IAM permissions, honouring `CP-PUL-012`
- [ ] `infra/gcp/tests/test_no_authoritative_iam.py` still passes — the fix is detection, not authoritative resources

## Related specs / docs

- [`docs/specs/cloud-platform-specs.md`](../specs/cloud-platform-specs.md) — `CP-PUL-012` (deploy SA privilege bounds), `CP-PUL-013` (additive IAM only)
- [`docs/llds/cloud-platform-gcp.md`](../llds/cloud-platform-gcp.md) — "IAM: additive only", "The Two-Stack Model"
- [`docs/planning/gcp-pulumi-replatform.md`](../planning/gcp-pulumi-replatform.md) — the phase roadmap this sits outside of
