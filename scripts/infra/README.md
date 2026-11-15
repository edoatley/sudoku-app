# scripts/infra

One-time / elevated infrastructure helpers and local operations scripts for AWS (`infra/aws`) and
GCP (`infra/gcp`).

## Layout

```text
scripts/infra/
├── aws/        — AWS setup + local ops (Terraform)
├── gcp/        — GCP helpers not expressible in Pulumi (smoke user + live IAM drift inventory)
├── shared/     — cross-cloud helpers (cloud target switcher, local secrets, Route53 NS delegation)
└── ../.env.local  (git-ignored) — secrets, written by shared/setup-local-secrets.sh
```

**AWS** uses `aws/bootstrap.sh` for one-time prerequisites before Terraform (`infra/aws`).
**GCP** (`infra/gcp`) is 100% Pulumi-built via the two-stack model (`bootstrap` and `app`), so all
former GCP bootstrap/IAM/WIF/DNS bash scripts have been retired; only the smoke-user script and the
live inventory/IAM-drift check remain.

## Conventions (all scripts follow these)

- `#!/usr/bin/env bash` + `set -euo pipefail`.
- Header block: one-line purpose, `Usage:` example, and a `@spec` ref where one applies.
- **Idempotent** — safe to re-run; existing resources are detected and skipped/updated.
- Inputs come from the environment first, then `../.env.local`, then `gcloud`/`aws` config.
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

| Script | Purpose | When |
|---|---|---|
| `create-smoke-user.sh` | Provision/reset the Identity Platform password smoke-test user (`CP-GCP-032`) | Once per project or to rotate `GCP_SMOKE_TEST_USER_PASSWORD` |
| `inventory.sh` | Compare live GCP project resources & IAM bindings against `sudoku-gcp-bootstrap` state (`make gcp-inventory`) | Anytime to verify zero drift |

## shared/

| Script | Purpose |
|---|---|
| `cloud-target.sh` | Inspect or flip `DEPLOY_TARGET` (`aws` \| `gcp` \| `both`) on GitHub Actions (`make cloud-target [TARGET=...]`) |
| `setup-local-secrets.sh` | Collect deploy secrets → `scripts/.env.local` (both clouds source this) |
| `delegate-dns.sh` | Generic Route53 NS-delegation tool (used for AWS Amplify subdomain zones and the one-time `gcp.edoatley.co.uk` delegation) |

## See also

- `docs/runbooks/gcp-manual-setup.md` — the GCP Pulumi setup and operations runbook.
- `docs/llds/cloud-platform-gcp.md` — GCP Pulumi architecture and two-stack privilege model.
