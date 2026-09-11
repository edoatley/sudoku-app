# Implementation Plan: GCP Pulumi — Phase 2, Bootstrap Stack

**Status**: Approved — ready to implement
**Created**: 2026-09-11
**Origin**: `docs/planning/gcp-pulumi-replatform.md` §4
**Arrow**: `cloud-platform` (`docs/arrows/cloud-platform.md`) — GCP facet

---

## 1. Goal

A brand-new GCP project, 100% Pulumi-built, holding: enabled APIs, the GCS Pulumi state bucket,
a Cloud KMS key for app-stack secrets, one Artifact Registry repository, three service accounts
with least-privilege role bindings, Workload Identity Federation for GitHub Actions, and the
billing budget.

This is the phase that **replaces `scripts/infra/gcp/bootstrap.sh` and `github-bootstrap.sh`**
(they are deleted in Phase 9, once nothing references them). It is also the phase that proves the
tenet revision: after it, CI can deploy but cannot grant itself privilege.

## 2. Project quota — checked, cleared

**Verified 2026-09-11: 20 projects remaining.** Peak usage during this migration is 6 (five
existing plus the new one), so there is ample headroom. This prerequisite is **satisfied**; no
increase request is needed.

How it was checked, for the record: project-creation quota is **not** exposed by any API or
`gcloud` command, and for a consumer account (no organisation — confirmed via
`gcloud organizations list` returning zero) it does not appear on the IAM & Admin → Quotas page
either, since that page serves *organisation* quotas. The reliable check is the
**New Project page** (`console.cloud.google.com/projectcreate`), which displays
"You have N projects remaining in your quota" **before** anything is created — and only when
fewer than 30 remain.

Two interactions to keep in mind rather than act on:

- A deleted project sits in `DELETE_REQUESTED` and **keeps consuming quota for 30 days**. Since
  the old project is retained 30 days post-cutover and then deleted, roughly two months elapse
  before its slot returns. Irrelevant at 20 free, but it is why deleting a dormant project is not
  a same-day way to free capacity.
- If quota ever did run out and an increase were refused, the fallback is to rebuild inside the
  existing project under a new Firestore database and Hosting site — which **forfeits the "100%
  Pulumi-built" property**. Not needed here.

## 3. The state chicken-and-egg

Pulumi cannot bootstrap its own state backend. Start on the local filesystem backend and migrate
into the bucket the stack itself created — this keeps "100% Pulumi-built" literally true:

```bash
cd infra/gcp/bootstrap
pulumi login --local
pulumi stack init prod --secrets-provider=passphrase
pulumi up                                            # creates project, billing, APIs, bucket, KMS, SAs, WIF

pulumi stack export --file bootstrap-state-backup.json   # safety net; keep until Phase 3 is green
pulumi login gs://sudoku-pulumi-state-<suffix>
pulumi stack init prod --secrets-provider=passphrase
pulumi stack import --file bootstrap-state-backup.json
pulumi refresh                                       # MUST report no changes
```

The bucket has versioning from birth, so the migration is recoverable. Documented fallback if
export/import misbehaves: create the bucket with three `gcloud` commands and `pulumi import` it.

**Why the bootstrap stack uses a passphrase, not KMS:** it is creating the KMS key, so it cannot
encrypt itself with it. It holds no secrets — the only secret in the system is the Google OAuth
client secret, which belongs to the `app` stack — so a passphrase is honest rather than a
weakness. Record this in the LLD rather than hiding it.

## 4. Resource inventory (ported from the bash scripts)

**APIs** — the fifteen from `bootstrap.sh:119-135`, with three changes:

- **drop** `secretmanager.googleapis.com` — after de-Bedrocking there is no secret to store
- **drop** `policytroubleshooter.googleapis.com` — a diagnostic, not a dependency
- **add** `cloudkms.googleapis.com` — the secrets provider

Remaining: `run`, `artifactregistry`, `firestore`, `firebase`, `firebasehosting`,
`identitytoolkit`, `dns`, `billingbudgets`, `pubsub`, `iamcredentials`, `sts`,
`cloudresourcemanager`, `aiplatform`.

**Service accounts and their project roles** (from `bootstrap.sh:182-218`):

| Service account | Roles |
| --- | --- |
| `sudoku-run` | `roles/datastore.user`, `roles/aiplatform.user` |
| `sudoku-image-recognition-run` | `roles/datastore.user`, **`roles/aiplatform.user` (NEW)** |
| `sudoku-deploy` | see below |

The image-recognition runtime SA gains `roles/aiplatform.user` because Phase 5 moves its
inference to Vertex. It has no such binding today.

**Deploy SA roles** — the seven from `github-bootstrap.sh:133-140` plus two, minus none:

`roles/datastore.owner`, `roles/firebase.admin`, `roles/dns.admin`,
`roles/serviceusage.serviceUsageAdmin`, `roles/run.admin`, `roles/iam.serviceAccountUser`,
`roles/artifactregistry.writer`, bucket-scoped `roles/storage.objectAdmin`, **plus
`roles/identityplatform.admin`** (Phase 4 needs it; verify the exact role id) **plus
`roles/cloudkms.cryptoKeyEncrypterDecrypter`** on the KMS key.

That last one is **mandatory and easy to miss** — without it every CI run dies with an opaque
decrypt error the first time the app stack reads a secure config value.

Explicitly **not** granted: any Secret Manager role, and any billing-account role.

**WIF** — pool `github-pool`, provider `github-provider`, issuer
`https://token.actions.githubusercontent.com`, mapping
`google.subject=assertion.sub,attribute.repository=assertion.repository`, condition
`assertion.repository == 'edoatley/sudoku-app'`. The condition is the security boundary: without
it any repository, including a fork, could mint a token and impersonate the deploy SA.

**Budget** — Pub/Sub topic, email notification channel, and `gcp.billing.Budget` at 80% actual +
100% forecast. **In this stack, not the app stack**, because the budget is scoped to the billing
account: granting a repo-federated CI identity `billing.budgets.*` would give it write access
across every project on that account.

## 5. Work items

- [x] **2a. Run the quota check** (§2) — done 2026-09-11, 20 projects remaining. Prerequisite cleared.
- [ ] **2b. Flesh out `bootstrap/__main__.py`** wiring `ProjectFoundation`, `ArtifactRegistry`,
      `ServiceIdentity` × 3, `WorkloadIdentityFederation`, and `CostGuardrails`.
      @spec CP-PUL-001, CP-PUL-002, CP-PUL-003, CP-PUL-010, CP-PUL-011, CP-PUL-060
- [ ] **2c. Deploy privileges** as a flat list of `gcp.projects.IAMMember`,
      `gcp.storage.BucketIAMMember` and `gcp.kms.CryptoKeyIAMMember` directly in `__main__.py` —
      **not** a component. It is a single-use list, and a component over one use is an
      abstraction for its own sake (Rule 2).
      @spec CP-PUL-012, CP-PUL-013
- [ ] **2d. `bootstrap/Pulumi.prod.yaml`** — project id, region, billing account, alert email,
      budget amount, GitHub repo.
- [ ] **2e. First `pulumi up` on the local backend**, then the state migration in §3.
      @spec CP-PUL-040, CP-PUL-041
- [ ] **2f. `protect=True`** on the state bucket, the KMS CryptoKey, and the KeyRing. Losing the
      key makes every stack's secrets permanently unreadable.
      @spec CP-PUL-042
- [ ] **2g. Add the transition GitHub secrets and vars** — `GCP_PROJECT_ID_NEXT`,
      `GCP_WIF_PROVIDER_NEXT`, `GCP_DEPLOY_SA_EMAIL_NEXT` (secrets), `PULUMI_STATE_BUCKET`,
      `PULUMI_KMS_KEY` (vars — neither is sensitive). The `_NEXT` suffix means the existing
      `deploy-gcp.yml` keeps working against the old project until Phase 8 renames them.
- [ ] **2h. Rewrite `docs/runbooks/gcp-manual-setup.md`** around the two stacks. The manual-steps
      table shrinks from five rows to two: the project/billing/state prelude, and the OAuth
      consent screen + client (Phase 4).
- [ ] **2i. A throwaway federation-check workflow** proving the WIF chain works and the privilege
      boundary holds (§6).

## 6. Definition of Done

- [ ] `gcloud projects describe <new-id>` succeeds; billing is linked
- [ ] No default VPC exists (`gcloud compute networks list` is empty) — `auto_create_network=False`
- [ ] `pulumi stack output` yields `project_id`, `project_number`, `state_bucket_url`,
      `kms_key_uri`, `wif_provider_name`, the three SA emails, and `artifact_registry_url`
- [ ] State lives in GCS (`pulumi login gs://… && pulumi stack ls` lists `prod`)
- [ ] `pulumi refresh` reports **no changes**
- [ ] A second `pulumi up` reports `0 changed`
- [ ] A throwaway GitHub Actions job authenticating with `google-github-actions/auth@v3` and the
      `_NEXT` secrets **can** `gcloud storage ls gs://<state bucket>` and **cannot**
      `gcloud billing budgets list` — this is the test that proves the privilege split, and it
      must actually be run, not assumed
- [ ] `gcloud iam service-accounts keys list` shows only Google-managed keys — zero long-lived
      credentials
- [ ] The old project and the live production deployment are **untouched**
- [ ] `CP-PUL-001..003`, `-010..013`, `-040..042`, `-060` flipped to `[x]`
- [ ] `docs/arrows/cloud-platform.md` and `docs/arrows/index.yaml` updated

## 7. Out of scope

- Firestore, Cloud Run, Hosting, DNS records, Identity Platform (Phases 3, 4, 6)
- Deleting `bootstrap.sh` / `github-bootstrap.sh` — they still point at the old project, which is
  still serving production. Deletion is Phase 9.
- Any change to the live `deploy-gcp.yml` path
