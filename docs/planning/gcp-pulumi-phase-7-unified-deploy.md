# Implementation Plan: GCP Pulumi — Phase 7, Unified Deploy Workflow

**Status**: Approved — ready to implement
**Created**: 2026-09-19
**Origin**: `docs/planning/gcp-pulumi-replatform.md` §4, `gcp-pulumi-handoff.md` §5
**Arrow**: `cloud-platform` (`docs/arrows/cloud-platform.md`) — cloud-general + GCP facets

---

## 1. Goal

One entry point for deployment. `ci-deploy.yml` decides which cloud a push or dispatch goes to, in
one place, and invokes the GCP deploy as a reusable workflow. Today that decision is implicit in
*which file has which push trigger*, spread across two workflows that cannot see each other.

Nothing about production changes. This phase moves the decision, it does not change any existing
answer.

## 2. Coherence pre-flight

The design is already written and still accurate: `docs/llds/cloud-platform.md` §*Deploy Target
Selection* specifies the three-level precedence, and all five specs exist unimplemented —
`CP-CD-001..004` and `CP-PUL-080`. No HLD change; no new spec IDs.

Two facts from Phase 6 that the handoff's Phase 7 outline predates:

| Handoff says | Actually |
| --- | --- |
| "`deploy-gcp.yml` becomes `workflow_call`-only and loses its eight phased inputs" | `deploy-gcp.yml` is the **Terraform** path to the incumbent project and is already dispatch-only; Phase 9 deletes it. The workflow to convert is **`deploy-gcp-pulumi.yml`**, which has no phased inputs to lose. |
| — | `teardown-gcp-pulumi.yml` now exists and keeps its own `delete` trigger. It is not a deploy entry point and stays as it is. |

## 3. Current shape

```
push main, rc-*  ──▶ ci-deploy.yml ──▶ gate-ui, gate-backend, changes,
                                       build-backend, build-image-recognition,
                                       deploy (AWS, inline), smoke-test, notify

push rcg-*       ──▶ deploy-gcp-pulumi.yml ──▶ build x2, pulumi, deploy-frontend, smoke
```

Target selection is the set of `branches:` filters. There is no way to express "deploy `main` to
GCP" without editing a workflow, and no way to see both paths at once.

## 4. Target shape

```
push main, rc-*, rcg-*  ──┐
workflow_dispatch(target) ─┴▶ ci-deploy.yml
                                ├─ gate-ui, gate-backend,
                                │  gate-infra, gate-pulumi,
                                │  gate-integration               (always — D2)
                                ├─ select-target                  (the only decision)
                                ├─ AWS jobs, inline               if aws
                                └─ deploy-gcp-pulumi.yml          if gcp   (workflow_call)
                                     ↓
                                   notify                          (aggregates both)
```

`select-target`, per the LLD:

1. A `workflow_dispatch` `target` input other than `auto` wins outright. → `CP-CD-003`
2. Branch `rc-*` → AWS; `rcg-*` → GCP. Absolute; does not consult the variable. → `CP-CD-002`
3. Branch `main` → `vars.DEPLOY_TARGET` (`aws` | `gcp` | `both`), default `aws`. → `CP-CD-001`

Where the target is `both`, each cloud deploys independently and the run is red if either
deployment or smoke fails. This selects *deploys*, not traffic — each cloud keeps its own stable
hostname, so flipping `DEPLOY_TARGET` is not a DNS failover. → `CP-CD-004`

## 5. Decisions

### D1 — AWS stays inline; only GCP becomes reusable

Asymmetric, and deliberately so. `CP-PUL-080` asks for the GCP deploy to be reusable; nothing asks
the same of AWS, and extracting the live AWS path into a called workflow would put the one thing
this phase must not break — production deployment — at risk for symmetry alone. Inline AWS jobs and
a called GCP workflow compose fine: each is gated on a `select-target` output.

Revisit at Phase 9, when the Terraform GCP workflow is deleted and the file count drops anyway.

### D2 — `rc-*` and `rcg-*` get an identical gate set

The gap is **gating, not coverage**, and it runs the opposite way to what the file layout suggests.
`ci.yml` fires on `['**', '!main', '!rc-*']`, so `rcg-*` already runs the full suite — including the
Terraform, Pulumi and integration jobs that `rc-*` never sees. What it does not do is *block the
deploy*: `deploy-gcp-pulumi.yml` builds and ships while those checks are still running.

| | `rc-*` today | `rcg-*` today | Both, after this phase |
| --- | --- | --- | --- |
| UI lint + unit | gate, blocking | `ci.yml`, parallel | gate, blocking |
| Backend unit | gate, blocking | `ci.yml`, parallel | gate, blocking |
| Terraform fmt + validate | — | `ci.yml`, parallel | gate, blocking |
| Pulumi lint + component tests | — | `ci.yml`, parallel | gate, blocking |
| Integration (compose + Playwright) | — | `ci.yml`, parallel | gate, blocking |
| Post-deploy smoke | `smoke-test` job | own job in the GCP workflow | unchanged, per cloud |

So the parity set is the **union**, gated: `ci-deploy.yml` gains `gate-infra`, `gate-pulumi` and
`gate-integration` mirroring `ci.yml`'s equivalents, and `ci.yml` adds `!rcg-*` to its push
exclusions. One rule results — deploying branches are gated by `ci-deploy.yml`, everything else is
checked by `ci.yml` — with no branch running both and no check lost.

Taking the union rather than the intersection means `main` and `rc-*` gain three gates they do not
have today. That is a deliberate widening of the regression bar in §8: the *deploy* behaves
identically, but a broken Terraform plan or a failing Pulumi component test will now stop a
production deploy that previously proceeded. Worth having, and it can only ever block a deploy that
should have been blocked.

Cost: `gate-integration` needs Docker and is the slowest job in the repo, so it adds a few minutes
to every deploy. Reuse `ci.yml`'s job definitions rather than retyping them — two copies of an
integration harness would drift.

### D3 — `cancel-in-progress` must not apply to the GCP deploy

`ci-deploy.yml` sets `cancel-in-progress: true`, deliberately, so Terraform state locks do not pile
up. Cancelling a `pulumi up` mid-apply is worse than queuing: it leaves a held lock that the next
run cannot break without `pulumi cancel`, and the stack sits half-applied.

`deploy-gcp-pulumi.yml` already declares its own `concurrency` with `cancel-in-progress: false`,
and a called workflow's own concurrency group still applies — so the GCP side is protected. **This
needs proving, not assuming**: a called workflow inheriting the caller's cancellation semantics is
exactly the kind of thing that behaves differently from the documentation. Work item 7f.

### D4 — where the selection logic lives

`select-target` is a three-level precedence over four cases whose failure mode is deploying the
wrong cloud — silently, since both deploys look healthy. It deserves real tests, and there is no
obvious home for them: the only pytest root in the repo is `infra/gcp/tests`, and this logic is
cloud-general.

| Option | Cost |
| --- | --- |
| **Shell in the job, plus a workflow-shape test** asserting the precedence order appears in the file | No new CI surface; the test pins the shape, not the behaviour |
| **Tested Python helper** in `scripts/github/`, a new pytest root, a `ci-scripts` job in `ci.yml` | Real unit tests over all cases; adds one small CI job and a pytest config |
| Helper under `infra/gcp/components/` | Free to test, but files the cloud-general decision under the GCP facet, where nobody will look for it |

**Decided: the tested helper.** `naming.py` already proves the "shared Python, shelled into from
CI" pattern in this repo, and this is higher-consequence logic than stack naming.

- `scripts/github/select_deploy_target.py` — a pure function over `(event_name, ref_name,
  dispatch_target, deploy_target_var)` returning the set of targets, raising on an unrecognised
  value.
- `scripts/github/tests/` with a `pyproject.toml` or `pytest.ini` alongside it.
- A `ci-scripts` job in `ci.yml` mirroring `ci-pulumi`'s shape (`setup-uv`, `uv sync --frozen`,
  `ruff`, `pytest`), and a line in `scripts/local/local-alltests.sh` so it is covered pre-push.

## 6. Work items

- [ ] **7a. Add `workflow_dispatch`** to `ci-deploy.yml` with `target: auto | aws | gcp | both`,
      default `auto`, and add `rcg-*` to the push branch filter.
- [ ] **7b. Add the `select-target` job** implementing §4's precedence, with outputs `aws` and
      `gcp`. Per D4.
      @spec CP-CD-001, CP-CD-002, CP-CD-003
- [ ] **7c. Gate the AWS jobs** on `needs.select-target.outputs.aws == 'true'`, preserving their
      existing `if:` conditions rather than replacing them.
- [ ] **7c2. Bring the gate sets to parity** per D2 — add `gate-infra`, `gate-pulumi` and
      `gate-integration` to `ci-deploy.yml`, reusing `ci.yml`'s definitions, and add `!rcg-*` to
      `ci.yml`'s push exclusions so no branch runs both and no check is lost.
- [ ] **7d. Convert `deploy-gcp-pulumi.yml` to `workflow_call`**, dropping its `push` and
      `workflow_dispatch` triggers, taking `stack` as an input and `secrets: inherit`.
      @spec CP-PUL-080
- [ ] **7e. Call it from `ci-deploy.yml`** gated on the `gcp` output, and extend `notify` to
      aggregate both clouds so `both` is red if either fails.
      @spec CP-CD-004
- [ ] **7f. Prove the concurrency semantics** of D3 — that a superseding push does not cancel an
      in-flight `pulumi up`. If it does, the fix is to drop `cancel-in-progress` for GCP-bound runs,
      not to accept a half-applied stack.
- [ ] **7g. Tests** — §7.
- [ ] **7h. Doc cascade** — flip `CP-CD-001..004` and `CP-PUL-080`; update `docs/llds/
      cloud-platform-gcp.md` §CI/CD, whose current text describes the pre-Phase-6 arrangement.
      Record in `docs/llds/cloud-platform.md` that deploying branches are gated by `ci-deploy.yml`
      and all others by `ci.yml` — the rule D2 establishes.

## 7. Tests

| Test | Asserts |
| --- | --- |
| dispatch `target=gcp` on `main` | GCP only, variable ignored (`CP-CD-003`) |
| dispatch `target=auto` on `rc-x` | AWS only — dispatch does not override the branch convention |
| push `rc-*`, `DEPLOY_TARGET=gcp` | AWS only; the branch convention is absolute (`CP-CD-002`) |
| push `rcg-*`, `DEPLOY_TARGET=aws` | GCP only, same reason |
| push `main`, variable unset | AWS only (`CP-CD-001` default) |
| push `main`, `DEPLOY_TARGET=both` | both true (`CP-CD-004`) |
| push `main`, `DEPLOY_TARGET=nonsense` | fails loudly rather than defaulting — a typo must not silently deploy to one cloud |
| `deploy-gcp-pulumi.yml` has no `push`/`workflow_dispatch` trigger | `CP-PUL-080`, added to `test_deploy_workflows.py` |
| no branch matches both `ci.yml` and `ci-deploy.yml` push filters | D2's single rule; a branch running both wastes CI minutes and a branch matching neither ships untested |
| every gate in `ci.yml` has a counterpart in `ci-deploy.yml` | D2's "no check lost" claim, asserted rather than trusted to review |

The first seven are unit tests over `scripts/github/select_deploy_target.py`; the last three are
workflow-shape assertions.

## 8. Verification, and what cannot be verified here

**The regression bar**: with `DEPLOY_TARGET` unset, a push to `main` must behave exactly as today —
same jobs, same order, same Terraform invocation.

Live paths this phase *can* exercise:

- an `rc-*` push → AWS, unchanged behaviour;
- an `rcg-*` push → GCP through the new entry point, with a full deploy, smoke and teardown.

Live paths it **cannot**: anything that deploys `main` to GCP. That would attach Cloud Run to the
new project's `prod` stack, which *is* the Phase 8 cutover. `target=gcp` and `target=both` on `main`
are therefore verified by unit test and by reading the resolved `select-target` outputs on a
dispatch that is then cancelled before the deploy jobs start — not by deploying. Say so in the PR
rather than implying full coverage.

**Deferred to Phase 8, not dropped.** The cutover is the first time `main` legitimately deploys to
GCP, so it is the natural place to exercise these paths for real. Phase 8's plan must carry three
checks that belong to Phase 7's specs rather than its own:

| Check | Spec |
| --- | --- |
| `DEPLOY_TARGET=gcp` on `main` deploys GCP and **not** AWS | `CP-CD-001` |
| `DEPLOY_TARGET=both` deploys both independently, and the run is red if either side fails | `CP-CD-004` |
| Flipping `DEPLOY_TARGET` moves no traffic — each cloud keeps its own hostname | `CP-CD-004` |

`CP-CD-001` and `CP-CD-004` therefore stay `[ ]` at the end of Phase 7 despite being implemented,
and flip in Phase 8 once exercised. Marking them `[x]` on unit tests alone would claim a
production behaviour nothing had run.

## 9. Related todo

Cancellation is handled narrowly here — D3 covers only the concurrency case. The general problem,
that a cancelled `pulumi up` leaves a held lock and a half-applied stack however it was cancelled,
is captured in [`docs/todo/prevent-partial-pulumi-applies.md`](../todo/prevent-partial-pulumi-applies.md).
