# Implementation Plan: GCP Re-Platform to Pulumi

**Status**: Approved — ready to implement
**Created**: 2026-09-11
**Arrow**: `cloud-platform` (`docs/arrows/cloud-platform.md`) — GCP facet; and `image-recognition` (Phase 5)
**Supersedes**: `docs/planning/old/gcp-terraform-infrastructure-plan.md` (the GCP facet's Terraform origin)

---

## 1. Context

The GCP target runs end-to-end in production today, but its infrastructure story is incoherent
and it is not independent of AWS.

**`infra/gcp/` Terraform manages only 11 resources** — two Cloud Run services and their IAM
members, a Firestore database with one TTL field and one composite index, a Firebase project +
Hosting site + custom domain, and three budget resources. Everything else is imperative bash:
`scripts/infra/gcp/bootstrap.sh`, `github-bootstrap.sh`, `identity-platform-bootstrap.sh`,
`bedrock-cross-cloud.sh`, `grant-prod-invoker.sh`, and `set-custom-domain-cname.sh` between them
create the project, fifteen API enablements, the state bucket, two Artifact Registry
repositories, three service accounts, every IAM binding, the Workload Identity Federation pool
and provider, the Identity Platform configuration, and the Secret Manager entries.

That split was deliberate — the HLD's second tenet reads *"prefer manual, least-privilege
identity on GCP over Terraform-managed IAM — the identity layer is a deliberate learning surface
kept out of automation."* **This plan deliberately revises that tenet.** The manual approach
produced six bash scripts that are the least-tested, least-reviewed and most drift-prone part of
the system, while the privilege concern it was protecting is better served by *splitting* the
deploying identity in two than by removing it from automation altogether.

Two AWS dependencies remain in the GCP path:

- **Image recognition still calls Bedrock unconditionally**, authenticated by a long-lived AWS
  access key held in GCP Secret Manager. The coach escaped to Vertex in PR #212; image
  recognition did not, so `CP-GCP-085` cannot be retired.
- **DNS lives in Route53** — `sudoku-gcp.edoatley.co.uk` is a CNAME in the `edoatley.co.uk` zone
  (`Z055000739D7L0ZGFAMC1`, AWS `backups` profile), upserted by a script that requires an
  authenticated `aws` session.

And there is **no deploy-target switch**: the target is the branch name (`rc-*` → AWS,
`rcg-*` → GCP), `main` deploys AWS only, and GCP production is reachable solely by manual
`workflow_dispatch`.

**Intended outcome.** A GCP target provisioned entirely by Pulumi (Python, `uv`) in a clean-room
project, with zero AWS runtime dependency and zero long-lived credentials, and a single GitHub
repository variable selecting which cloud a merge to `main` deploys.

**What this plan does not do.** It does not touch `infra/aws/` — AWS stays on Terraform,
permanently. The split is by cloud, never within one cloud.

---

## 2. Decisions (from the design pass)

| Decision | Choice |
| --- | --- |
| Tool + language | Pulumi, Python, dependencies via `uv` (`runtime.options.toolchain: uv`) |
| Migration style | **Clean room** — a brand-new GCP project, 100% Pulumi-built. No `pulumi import`; adoption must never shape the code. |
| Data | Firestore starts **empty**. No export/import. |
| Identity continuity | Automatic — `UserIdentityResolver` returns the raw Google `sub`, not the Firebase UID, so a Google user signing into the new tenant keeps the same canonical `userId`. **Verified empirically in Phase 4, not assumed.** |
| Stack layout | Two Pulumi projects: `sudoku-gcp-bootstrap` (human credentials) and `sudoku-gcp-app` (CI, WIF deploy SA), linked by a `StackReference` |
| Environments | Pulumi stacks replace Terraform workspaces. `default` → **`prod`**; ephemeral `rcg-*` per branch. |
| State | Self-managed GCS backend (`gs://sudoku-pulumi-state-<suffix>`) |
| Secrets | `app` stack: Cloud KMS (`gcp-kms://…`). `bootstrap` stack: passphrase — it cannot use a key it is itself creating, and it holds no secrets. |
| Budgets | In **`bootstrap`**, not `app`. `gcp.billing.Budget` is scoped to the *billing account*, so granting a repo-federated CI identity budget write access would span every project on that account. |
| DNS | Pulumi owns a Cloud DNS zone for `gcp.edoatley.co.uk`; one manual NS delegation in Route53. New host `sudoku.gcp.edoatley.co.uk`; `sudoku-gcp.edoatley.co.uk` retired. |
| Image recognition | `IMAGE_AI_PROVIDER=bedrock\|vertex` mirroring the coach's `CoachAiClient` port; `gemini-2.5-flash` on Vertex; Bedrock retained for the AWS Lambda. |
| Cutover gate | A fixture-based accuracy harness over the existing seven `tests/e2e_config.json` fixtures, Vertex ≥ Bedrock Haiku on mean cell accuracy and exact-grid count. |
| Deploy target | Repo variable `DEPLOY_TARGET` (`aws`\|`gcp`\|`both`, default `aws`) governs `main` only. `rc-*`/`rcg-*` keep their branch convention. A `workflow_dispatch` `target` input overrides both. |
| Deploy-target semantics | Deploy **selection** only. Each cloud keeps its own stable hostname; this is not a DNS failover. |
| Old project | Retained 30 days after cutover as rollback, then deleted — **blocked by** the Cognito OAuth-client migration (§6). |
| Secret Manager | Removed entirely. After de-Bedrocking, the only secret left is the Google OAuth client secret, which belongs in Pulumi's KMS-encrypted config. The project ends with zero long-lived credentials. |

### Tenet revision

HLD tenet 2 is replaced by a **two-stack privilege split**: a human-credentialed `bootstrap`
stack owns the project, billing, KMS, service accounts, IAM and federation; a CI-credentialed
`app` stack owns only application resources, cannot grant itself privilege, and holds no
billing-account permission. The identity layer remains the learning surface — it becomes
*declared and reviewable* rather than *hand-typed and undocumented*.

Net privilege change for the CI deploy identity: **+1 role** (`roles/identityplatform.admin`),
**−all Secret Manager roles**, **no billing reach**. Strictly better than today.

A fourth tenet is added: **prefer no cross-cloud runtime credentials** — each cloud's deployment
authenticates only with that cloud's native identity.

---

## 3. What "tests-first" means here

Most of this arrow is infrastructure, and the AWS facet's accepted gap ("no infrastructure tests
exist — no Terratest or equivalent") has stood because HCL offers no cheap unit-test seam.
**Pulumi does.** `pulumi.runtime.set_mocks()` lets every `ComponentResource` be instantiated and
asserted on with no cloud contact, so Phase 1 delivers the first real infrastructure unit tests
in this repository — before any resource is created.

The layered acceptance checks are:

| Layer | Check |
| --- | --- |
| Static | `uv run ruff check infra/gcp`, `uv run pytest infra/gcp/tests` |
| Structural | `test_no_authoritative_iam.py` — fails the build if any component uses `IAMPolicy` or `IAMBinding` instead of the additive `IAMMember` |
| Plan-time | `pulumi preview` clean on every PR touching `infra/gcp/**` |
| Idempotence | A second `pulumi up` reports `0 changed` — a drift signal Terraform never gave this facet |
| Runtime | The existing smoke scripts (`scripts/github/gcp-smoke-token.sh`, `api-smoke-tests.sh`) against each phase's deployed surface |
| Accuracy | `pytest -m accuracy` over the seven image fixtures, compared against a committed per-provider baseline |

The accuracy harness is **credential-bearing and costs money per run**, so like the existing
`e2e` and `coach-quality` suites it stays out of both CI and the mandatory pre-push gate. It is
an on-demand decision tool whose summary is pasted into the Phase 5 PR body — the same practice
used for the coach's invoke/converse A/B.

---

## 4. Phased work

Ten increments, one PR each. Every phase leaves `main` green and deployable. Phases 1–6 build the
new project while the **old project keeps serving production**, so none of them is a risky change.

| # | Phase | Leaves behind | Depends on |
| --- | --- | --- | --- |
| 0 | Documentation & drift fixes | This plan; revised HLD/LLD/EARS/arrows; three drift corrections | — |
| 1 | Pulumi scaffolding + validation gate | The component library, unit-tested. **No cloud resources.** | 0 |
| 2 | Bootstrap stack | The new GCP project, GCS state, KMS, Artifact Registry, 3 SAs, WIF, budget | 1 |
| 3 | App stack — data, hosting, DNS | Firestore, Firebase Hosting site, Cloud DNS zone + NS delegation | 2 |
| 4 | Identity Platform | Google sign-in works on the new project; smoke user mints tokens | 3 |
| 5 | De-Bedrock image recognition | `IMAGE_AI_PROVIDER` switch + Vertex adapter + accuracy harness | 0 (**parallel**) |
| 6 | App stack — compute + frontend | A full end-to-end env on an ephemeral `rcg-*` stack | 4, 5 |
| 7 | Unified deploy workflow | `DEPLOY_TARGET`; `deploy-gcp.yml` becomes `workflow_call`-only | 6 |
| 8 | **Production cutover** | `sudoku.gcp.edoatley.co.uk` served from the new project | 7 |
| 8b | Cognito OAuth-client migration | AWS federation on its own OAuth client | 4 (**parallel**) |
| 9 | Decommission | `infra/gcp/*.tf` and six bash scripts deleted; AWS key destroyed | 8, 8b |

```
                    ┌──────────────────────────────────────────────────────────┐
P0 docs ────────────┤                                                          │
                    │                                                          ▼
                    ├──▶ P1 scaffolding ──▶ P2 bootstrap ──▶ P3 data/DNS ──▶ P4 identity ──┐
                    │      (unit tests)      (new project)    (delegation)   (OAuth client)│
                    │                                                             │        │
                    └──▶ P5 image-rec Vertex + accuracy harness ───────────────┐  │        │
                             (pure app code — fully parallel)                  ▼  ▼        ▼
                                                                        P6 compute + frontend
                                                                                  │
                                                                                  ▼
                                                                        P7 unified workflow
                                                                                  │
                                                                                  ▼
                                                                        P8 PRODUCTION CUTOVER
                                                                                  │
       P4 ──▶ P8b Cognito OAuth-client migration ─────────────────────────────────┤
                  (parallel from P4; blocks the day-30 deletion)                  ▼
                                                                        P9 decommission
                                                                                  │
                                                                  (backlog, cutover+30d)
                                                                    delete old project
```

**Strictly serial**: 1 → 2 → 3 → 4 → 6 → 7 → 8 → 9, each depending on the previous phase's live
cloud state.
**Parallel**: 5 with 1–4 (zero shared files — `image_recognition/**` vs `infra/gcp/**`); 8b with
5–7 (touches only `infra/aws/cognito.tf` and two secrets).

**Hard gates**:

- Phase 2 is gated on **two** quota checks (§6, R1): project creation (cleared — 20 remaining) and **billing-account linkage** (limit 5; hit on first apply, resolved by unlinking a dormant project).
- Phase 4 is gated on the manual OAuth consent screen + client, and on the **`sub` continuity
  verification**.
- Phase 6's `IMAGE_AI_PROVIDER=vertex` is gated on Phase 5's **accuracy comparison**.
- Phase 8 is gated on **NS delegation propagation** and **Google-managed cert issuance**.
- Deleting the old project is gated on **Phase 8b**.

Detailed increment plans: `gcp-pulumi-phase-1-scaffolding.md`, `gcp-pulumi-phase-2-bootstrap.md`.
Phases 3–9 are detailed just ahead of each phase, so they can absorb what the preceding phase
learned.

---

## 5. Spec ownership within this arrow

**New — `CP-CD-001..004`** (cloud-general, deploy-target selection) and **`CP-PUL-001..082`**
(the GCP facet's Pulumi realisation). `CP-PUL` is a new facet token: the realisation surface
changed wholesale, and the `CP-GCP` block (001–091) is genuinely crowded.

**Superseded** (struck through with a note, per the `CP-INFRA-061` precedent):
`CP-GCP-014` (manual prod invoker), `-031` (Identity Platform outside Terraform), `-050`
(Route53 CNAME, "no Cloud DNS zone"), `-081` (`terraform fmt`/`validate` in CI), `-082` (bash
bootstrap), `-083` (SAs/IAM/WIF/IdP outside Terraform), `-085` (cross-cloud Bedrock key —
struck at Phase 9).

**Reworded, staying `[x]`**: `CP-GCP-020` (workspaces → stacks), `CP-GCP-070`
(`managed_by=pulumi`), `CP-GCP-080` (WIF, still true).

**Flipped `[ ]` → `[x]` in Phase 0**: `CP-GCP-090` — the coach's Vertex cutover landed in
`337f748` / PR #212 but the marker was never flipped. Pre-existing drift, corrected here.

**Image recognition**: new `IR-AI-001..006` (provider port, Vertex adapter, ADC auth, shared
prompts, error wrapping, provider-scoped model lists) and `IR-TEST-001..003` (accuracy harness,
baseline, repeat-run comparison). `IR-GCP-005` struck through.

**Remaining `[D]`**: `CP-GCP-061` (budget hard-cap) and `CP-GCP-091` (private VPC egress) are
untouched by this plan.

---

## 6. Risks

| # | Risk | Mitigation |
| --- | --- | --- |
| R1 | **Two separate quotas gate Phase 2, not one.** (a) *Project creation* — how many projects you may create. (b) *Billing-account linkage* — how many projects may be linked to one billing account. They have different limits and different increase forms. | (a) **cleared 2026-09-11**, 20 remaining, checked on the New Project page. (b) **hit on 2026-09-12**: the limit is **5 linked projects** and all five were in use, so `pulumi up` failed at the billing-link step with `Cloud billing quota exceeded` — *after* creating the project. Resolved by unlinking billing from a dormant project (`gcloud billing projects unlink <id>` — reversible, deletes nothing). Check both before a rebuild: `gcloud billing projects list --billing-account <id> \| wc -l`. Increase form: support.google.com/code/contact/billing_quota_increase. |
| R2 | **The Google OAuth client is shared with AWS Cognito and lives in the old project.** Deleting that project at day 30 breaks AWS federation. | There is no API to create OAuth client IDs — irreducibly manual on both clouds. Google's `sub` is stable per account across clients, so `userId` is unaffected — but **verify empirically in Phase 4**. Create a second client for Cognito and repoint `infra/aws/cognito.tf` (Phase 8b), tracked as a backlog row blocking the deletion. |
| R3 | **Identity Platform may not be initialisable as code.** `gcp.identityplatform.Config` claims it can enable the entitlement; the current script walks the user through a console click. | Test against the fresh project in Phase 4 — a clean room is the ideal place to find out. Fallbacks in order: one documented click, or a `pulumi_command.local.Command` wrapping the same `curl`. Do not design assuming the optimistic answer. |
| R4 | **Firebase custom-domain cert issuance is asynchronous** (minutes to ~24h) and the resource returns before it completes. | A `sudoku:waitForDomain` config flag — false on the first prod apply, true thereafter — plus a polling smoke step with generous retries that does not fail the pipeline on the first apply. |
| R5 | **Cloud DNS delegation propagation.** | Strict ordering: create the zone in Phase 3, delegate, verify with `dig +trace NS gcp.edoatley.co.uk`, and only enable the custom domain in Phase 8. TTL 300 on the NS record. |
| R6 | **`pulumi-gcp` coverage vs Terraform.** | Largely a non-risk and in fact a simplification: `pulumi-gcp` is generated from the merged GA+beta upstream providers, so the `provider = google-beta` lines disappear. `gcp.firebase.{Project,HostingSite,HostingCustomDomain}` and `gcp.identityplatform.{Config,DefaultSupportedIdpConfig}` all exist, and `Config` exposes `authorizedDomains`. Per-resource arg shapes are verified in Phase 1 (§7). Escape hatch: `pulumi_command.local.Command` with paired create/delete — **not** `pulumi-google-native`, which is archived. |
| R7 | **Destroy hazards** (not "losing `terraform destroy`" — `pulumi destroy` is a direct and better-equipped equivalent). | Forbid `--force` in the teardown workflow and say why; document `pulumi cancel` for stale GCS locks; `protect=True` on the prod Firestore database, Hosting site, DNS zone, KMS key and state bucket; guard teardown against the `prod` stack name. |
| R8 | **Losing the KMS key** makes every stack's secrets permanently unreadable. | Partly self-mitigating (keys cannot be deleted, only versions destroyed, with a ≥24h delay) but still `protect=True`, plus a runbook note that the key must never move key rings. |
| R9 | **The deploy SA silently losing KMS decrypt** produces an opaque CI failure. | The bootstrap stack owns `roles/cloudkms.cryptoKeyEncrypterDecrypter` declaratively; the runbook names the exact error. |
| R10 | **`DEPLOY_TARGET=both` doubles production blast radius.** | Keep it at `aws` until Phase 8 is verified; the notify job reports per-cloud so a partial failure is legible. |
| R11 | **The bootstrap state export/import** (§7) could go wrong. | `pulumi stack export --file` before migrating; the bucket has versioning from birth; `pulumi refresh` immediately after import proves state matches reality. Documented `gcloud`-then-`pulumi import` fallback. |
| R12 | **Vertex vision loses the accuracy comparison.** | The switch defaults to `bedrock`, so Phase 5 merges regardless. The consequence — Phases 6 and 9 keep the AWS key and cannot delete `bedrock-cross-cloud.sh` — is an explicitly stated partial outcome, not a blocked plan. |
| R13 | **Dual-running cost.** | Both projects scale to zero; the delta is one Cloud DNS zone (~$0.20/month, the first standing charge on the GCP side) plus Firestore storage. Recorded against the free-tier tenet rather than quietly absorbed. |

---

## 7. Open questions to resolve during implementation

Flagged rather than guessed. Each is cheap to settle in Phase 1 or 2.

- `runtime.options.toolchain: uv` minimum Pulumi CLI version, and whether `virtualenv:` resolves
  relative to `Pulumi.yaml`. Fallback: explicit `uv venv && uv sync` in CI.
- `StackReference` name format on a self-managed GCS backend (`organization/<project>/<stack>`
  vs bare `<stack>`) — confirm with `pulumi stack ls --all` against the real bucket.
- Whether `pulumi up -c/--config` (non-persisting, per-run) exists on the pinned CLI.
- `gcp.artifactregistry.Repository` cleanup-policy Python arg classes, and whether
  `cleanup_policy_dry_run=False` must be set explicitly.
- `gcp.organizations.Project` deletion-policy arg name (changed across upstream 5.x → 6.x).
- Whether `gcp.identityplatform.Config` can *initialise* Identity Platform (R3).
- The exact `roles/identityplatform.admin` role id.
- `gcp.firebase.HostingCustomDomain`'s `wait_dns_verification` arg name, and whether Firebase
  wants a CNAME or A+TXT for this subdomain. **Hard-code the known-working CNAME**; driving
  RecordSets off `required_dns_updates` is an elegant-but-fragile follow-up.
- `firebase deploy --only hosting:<site>` targeting semantics in firebase-tools ≥13 — the
  documented-safe form is `firebase target:apply hosting <alias> <siteId>` then
  `--only hosting:<alias>`.
- `google-genai` Python API surface (`types.Part.from_bytes`, `system_instruction` placement) at
  the pinned version. This SDK moves fast; pin it.

### The bootstrap state chicken-and-egg

Pulumi cannot bootstrap its own state backend. Resolved by starting on the local filesystem
backend and migrating into the bucket the stack itself created, which keeps "100% Pulumi-built"
literally true:

```bash
pulumi login --local
pulumi stack init prod --secrets-provider=passphrase
pulumi up                                            # creates project, billing, APIs, bucket, KMS, SAs, WIF

pulumi stack export --file bootstrap-state-backup.json
pulumi login gs://sudoku-pulumi-state-<suffix>
pulumi stack init prod --secrets-provider=passphrase
pulumi stack import --file bootstrap-state-backup.json
pulumi refresh                                       # proves imported state matches reality
```

---

## 8. Out of scope (separate arrows, or explicitly not planned)

- **Migrating `infra/aws/` to Pulumi.** AWS stays on Terraform, permanently.
- **Firestore data migration.** Decided against; the GCP leaderboard and game history reset at
  cutover. The old project remains for 30 days if a manual export is later wanted.
- **DNS failover.** `DEPLOY_TARGET` selects deploys, not traffic. Each cloud keeps its own
  hostname.
- **`CP-GCP-061`** (budget hard-cap) and **`CP-GCP-091`** (private VPC egress) remain `[D]`.
- **`UM-GCP-008`** (GCP admin authorization) and **`GL-GCP-006`** (single-active-game Firestore
  transaction) remain deferred on their own arrows.
- **The AWS Cognito → Google-`sub` re-key**, still deferred in the HLD.
