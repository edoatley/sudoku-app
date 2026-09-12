# GCP Setup Runbook

**Updated**: 2026-09-12 for the Pulumi re-platform.

Almost everything this runbook used to describe by hand is now code. The previous 450-line
`gcloud` procedure — which still describes the **legacy `sudoku-app-eo` project**, live until
cutover — is in git history at `cf0ccf6`:

```bash
git show cf0ccf6:docs/runbooks/gcp-manual-setup.md
```

Design: `docs/llds/cloud-platform-gcp.md`. Plan: `docs/planning/gcp-pulumi-replatform.md`.

---

## What is still manual, and why

Five things. None of them is a resource Pulumi could create.

| Step | Why it cannot be code | When |
| --- | --- | --- |
| `gcloud auth application-default login` | Authenticating is an action, not a resource. Pulumi has to run *as* somebody. | before Phase 2 |
| The one-time state migration (§2) | Pulumi cannot store state in a bucket that does not exist yet. | Phase 2, once |
| OAuth consent screen + OAuth 2.0 client | **No GCP API creates OAuth client IDs.** Irreducible on both clouds. (`CP-PUL-032`) | Phase 4 |
| NS delegation of `gcp.edoatley.co.uk` in Route53 | The parent zone lives in another cloud and is deliberately out of scope. One record, once. | Phase 3 |
| Identity Platform smoke-test user | No IaC resource exists for it; `scripts/infra/gcp/create-smoke-user.sh` is retained. (`CP-PUL-031`) | Phase 4 |

Everything else — the project, 14 API enablements, the state bucket, KMS, Artifact Registry,
three service accounts, every IAM binding, Workload Identity Federation, the billing budget,
Firestore, Cloud Run, Identity Platform config, Hosting and DNS records — is Pulumi.

---

## 1. Prerequisites

```bash
brew install pulumi                      # CLI; uv and gcloud are assumed present
gcloud auth login                        # as a principal that can create projects
gcloud auth application-default login    # ADC, which Pulumi's provider uses
cd infra/gcp && uv sync                  # resolve the Python deps
```

You need rights to **create projects** and to **attach the billing account**
(`010F10-A51056-E8EC40`). Confirm headroom before starting — project quota is a hard blocker
and increases take days:

```bash
# The New Project page reports remaining quota BEFORE creating anything, and is the only
# reliable source: the limit is exposed by no API, and IAM & Admin -> Quotas serves
# *organisation* quotas, which do not exist for this account.
open https://console.cloud.google.com/projectcreate
```

*Checked 2026-09-11: 20 remaining, against a peak need of 6.*

---

## 2. Bootstrap stack — the project itself

This creates the project, links billing, enables the APIs, and provisions the state bucket, KMS
key, Artifact Registry, service accounts, IAM and WIF. **Run it locally, as yourself.** It holds
exactly the privileges the CI identity deliberately does not.

Pulumi cannot store state in a bucket it has not created yet, so the first run uses the local
filesystem backend and then migrates. This is a one-time procedure; every later `pulumi up`
talks to GCS directly.

```bash
cd infra/gcp/bootstrap

# 2a. First run, on the local backend
pulumi login --local
export PULUMI_CONFIG_PASSPHRASE='<choose one; store it in your password manager>'
pulumi stack init prod --secrets-provider=passphrase
pulumi preview                     # READ THIS. It creates a real project and links billing.
pulumi up

# 2b. Migrate the state into the bucket it just created
pulumi stack export --file bootstrap-state-backup.json    # keep until Phase 3 is green
pulumi login gs://$(pulumi stack output state_bucket_url | sed 's|gs://||')
pulumi stack init prod --secrets-provider=passphrase
pulumi stack import --file bootstrap-state-backup.json
pulumi refresh                     # MUST report no changes
pulumi up                          # MUST report 0 changed
```

**The passphrase is not recoverable.** Losing it makes this stack's state unreadable. The stack
holds no secrets, so the blast radius is limited to needing a re-import, but store it anyway.

If the export/import misbehaves, the documented fallback is to create the bucket with three
`gcloud` commands and `pulumi import` it — see the plan, §7.

### If the Budget fails with SERVICE_DISABLED

```
The billingbudgets.googleapis.com API requires a quota project, which is not set by default
... "consumer": "projects/764086051850"
```

That consumer is Google's default client project, not yours. The Cloud Billing Budgets API
demands a quota project and human ADC does not supply one, so the call is attributed there and
the error reads as if the API were disabled. `Pulumi.prod.yaml` already sets
`gcp:userProjectOverride` and `gcp:billingProject` to fix this — if you hit it, confirm those two
are present. Service-account credentials (how CI authenticates) carry an implicit quota project
and are unaffected.

### Verify

```bash
pulumi stack output                              # 10 outputs, all populated
gcloud projects describe sudoku-eo-2026
gcloud compute networks list --project sudoku-eo-2026    # MUST be empty (auto_create_network=False)
gcloud iam service-accounts keys list \
  --iam-account sudoku-deploy@sudoku-eo-2026.iam.gserviceaccount.com \
  --project sudoku-eo-2026                       # Google-managed keys only — zero long-lived
```

---

## 3. GitHub secrets and variables

Set from the stack outputs. The `_NEXT` suffix means the existing `deploy-gcp.yml` keeps
running against the old project until the Phase 8 cutover renames them.

```bash
cd infra/gcp/bootstrap
gh secret set GCP_PROJECT_ID_NEXT      --body "$(pulumi stack output project_id)"
gh secret set GCP_WIF_PROVIDER_NEXT    --body "$(pulumi stack output wif_provider_name)"
gh secret set GCP_DEPLOY_SA_EMAIL_NEXT --body "$(pulumi stack output deploy_service_account_email)"

# Neither is sensitive — a bucket name and a KMS key path.
gh variable set PULUMI_STATE_BUCKET --body "$(pulumi stack output state_bucket_url)"
gh variable set PULUMI_KMS_KEY      --body "$(pulumi stack output kms_key_uri)"
```

### Verify the privilege split

The point of the two-stack model is that CI **cannot** grant itself anything. Prove it rather
than assume it — a federated job should reach the state bucket and be refused billing:

```bash
gcloud storage ls "$(pulumi stack output state_bucket_url)"   # expected: succeeds
gcloud billing budgets list --billing-account 010F10-A51056-E8EC40   # expected: PERMISSION_DENIED
```

Run these **as the deploy service account**, not as yourself — impersonate it, or run the
throwaway federation-check workflow. As yourself both will succeed and prove nothing.

---

## 4. Later phases

| Phase | Manual step |
| --- | --- |
| 3 | Take `pulumi stack output name_servers` from the app stack and add the NS delegation for `gcp.edoatley.co.uk` in the Route53 `edoatley.co.uk` zone (`Z055000739D7L0ZGFAMC1`, AWS `backups` profile). Verify with `dig +trace NS gcp.edoatley.co.uk`. |
| 4 | Configure the OAuth consent screen (external, `edoatley.co.uk` authorized, email/profile scopes only) and create a Web OAuth client. Add `https://sudoku-eo-2026.firebaseapp.com/__/auth/handler` to its redirect URIs. Then `pulumi config set --secret sudoku:googleOauthClientSecret`. |
| 4 | Run `scripts/infra/gcp/create-smoke-user.sh` against the new project. |
| 4 | **Verify identity continuity**: sign in as a real Google user and confirm the token's `firebase.identities["google.com"][0]` matches the `userId` already in the old project's Firestore `players`. Cheap to check, catastrophic to assume. |

The new project needs its **own** OAuth client. The existing one lives in the old project and is
shared with AWS Cognito's Google federation, so retiring that project requires a second client
for Cognito first — tracked as a backlog row blocking the deletion.
