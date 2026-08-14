# scripts/infra

One-time / elevated infrastructure setup that is **not** owned by Terraform — because it is a
prerequisite (project, state bucket, APIs), needs elevated access (IAM, WIF, public invoker), spans
both clouds, or has no reliable API (Identity Platform enable). Terraform (`infra/aws`, `infra/gcp`)
references what these scripts create by value.

## Layout

```
scripts/infra/
├── aws/        — AWS-only setup + local ops
├── gcp/        — GCP-only setup (one small script per concern)
├── shared/     — cross-cloud, used by both aws/ and gcp/
└── ../.env.local  (git-ignored) — secrets, written by shared/setup-local-secrets.sh
```

The two clouds are shaped differently on purpose: **AWS** leans on a single big `aws/bootstrap.sh`
(plus a few local-ops helpers), while **GCP** is split into one small script per concern
(`gcp/bootstrap.sh` does project/APIs/registry/SAs; the rest are separate elevated/manual steps that
run at different points in the deploy). Cross-cloud scripts live in `shared/`.

## Conventions (all scripts follow these)

- `#!/usr/bin/env bash` + `set -euo pipefail`.
- Header block: one-line purpose, `Usage:` example, and a `@spec` ref where one applies.
- **Idempotent** — safe to re-run; existing resources are detected and skipped/updated.
- Inputs come from the environment first, then `../.env.local`, then `gcloud`/`aws` config
  (e.g. `PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project)}"`). Env always wins.
- Secrets are loaded from `scripts/.env.local` (never hard-coded); populate it once with
  `shared/setup-local-secrets.sh`.
- Fail loud: missing prerequisites `exit 1` with a message pointing at the fix.

## aws/

| Script | Purpose |
|---|---|
| `bootstrap.sh` | One-time AWS prerequisites before the first `terraform apply` (state, ECR, etc.) |
| `add-admin.sh` | Add a user to the Cognito `administrators` group |
| `deploy-local.sh` | Run the Terraform plan+apply locally, mirroring the deploy workflow |
| `destroy-rc.sh` | Destroy a local `rc-*` Terraform workspace |
| `bedrock-quota-report.sh` | Report AWS Bedrock quotas / print increase commands |
| `test-budget-deny.sh` | Exercise the budget-deny cost guard without waiting |

## gcp/

Run in the order documented in `docs/runbooks/gcp-manual-setup.md` (it is the learning surface).

| Script | Purpose | When |
|---|---|---|
| `bootstrap.sh` | Project + billing, state bucket, APIs, Artifact Registry, runtime SAs + `datastore.user` | first, once |
| `github-bootstrap.sh` | Deploy-SA roles + Workload Identity Federation + GitHub secrets | after bootstrap |
| `identity-platform-bootstrap.sh` | Google + Email/Password sign-in, authorized domains (incl. custom domain) | before first apply (also invoked by `bootstrap.sh`) |
| `bedrock-cross-cloud.sh` | AWS Bedrock key → GCP Secret Manager + runtime-SA access (cross-cloud) | before `enable_coach=true` |
| `create-smoke-user.sh` | Provision the Identity Platform password smoke-test user (CP-GCP-032) | for CI/agent auth |
| `grant-prod-invoker.sh` | `allUsers` `run.invoker` on the **prod** services (non-default is in Terraform) | after first prod apply |
| `delegate-dns.sh` | Delegate `sudoku-gcp.edoatley.co.uk` to Cloud DNS (wraps `shared/delegate-dns.sh`) | after prod apply with `enable_custom_domain=true` |
| `apply-custom-domain-dns.sh` | UPSERT Firebase's required A/AAAA/TXT records (from the terraform output) into the Cloud DNS zone | after delegation, once Firebase has computed them |

## shared/

| Script | Purpose |
|---|---|
| `setup-local-secrets.sh` | Collect deploy secrets → `scripts/.env.local` (both clouds source this) |
| `delegate-dns.sh` | Generic Route53 NS-delegation tool (used for AWS Amplify domains and by `gcp/delegate-dns.sh`) |

## See also

- `docs/runbooks/gcp-manual-setup.md` — the GCP setup walkthrough + ordering.
- `docs/planning/gcp-dns-deployment-plan.md` — the prod / DNS-host bring-up plan.
- `scripts/README.md` — the wider `scripts/` overview (local + github helpers).
