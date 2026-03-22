# Scripts

Helper scripts for local development and infrastructure management.

## Prerequisites

All scripts require:
- `aws` CLI configured with `AWS_PROFILE=sandbox`
- `terraform` installed
- `gh` CLI installed and authenticated (bootstrap only)

---

## bootstrap-oidc.sh

**Run once** before the first Terraform apply. Creates the AWS prerequisites that Terraform itself cannot manage (chicken-and-egg):

- S3 bucket for Terraform state (`sudoku-tf-state`)
- GitHub Actions OIDC provider
- IAM role `sudoku-github-actions-deploy` with the inline deploy policy

Safe to re-run — all steps are idempotent. Re-running updates the trust policy and inline policy in-place, which is how IAM permission changes are applied (e.g. after adding new actions to the policy).

```bash
bash scripts/bootstrap-oidc.sh
```

---

## setup-local-secrets.sh

**Run once** to populate `scripts/.env.local` with the secrets needed by the local deploy and destroy scripts. Prompts for each value interactively (input is not echoed). Re-running lets you update individual values while keeping the rest.

The generated file is `chmod 600` and git-ignored.

```bash
bash scripts/setup-local-secrets.sh
```

Secrets collected:

| Variable | Description |
|---|---|
| `AMPLIFY_GITHUB_TOKEN` | GitHub classic token (repo scope) for Amplify repository connection |
| `GOOGLE_CLIENT_ID` | Google OAuth 2.0 client ID for Cognito social login |
| `GOOGLE_CLIENT_SECRET` | Google OAuth 2.0 client secret |
| `SMOKE_TEST_USER_EMAIL` | Email address of the Cognito smoke-test user |
| `SMOKE_TEST_USER_PASSWORD` | Password of the Cognito smoke-test user |

> **Tip:** The smoke-test user credentials can be recovered from Terraform state if lost:
> ```bash
> AWS_PROFILE=sandbox aws s3 cp s3://sudoku-tf-state/sudoku/terraform.tfstate - \
>   | jq -r '.resources[] | select(.type=="aws_cognito_user") | .instances[].attributes | "\(.username) \(.password)"'
> ```

---

## deploy-local.sh

Runs a full Terraform plan + apply locally, mirroring the `deploy` job in `deploy.yml`. Skips the Amplify build trigger and smoke tests — infrastructure only.

Automatically:
- Resolves the Terraform workspace from the branch name (`main` → `default`/prod, `rc-*` → named workspace)
- Builds the Lambda zip via Maven if not present locally and not found in S3
- Pauses for confirmation before applying the plan
- Tightens CORS and Cognito callback URLs after apply (same post-apply steps as CI)

```bash
# Deploy current git branch (default)
bash scripts/deploy-local.sh

# Deploy a specific branch
bash scripts/deploy-local.sh main
bash scripts/deploy-local.sh rc-myfeature
```

Secrets are loaded automatically from `scripts/.env.local` if present (run `setup-local-secrets.sh` first), or can be passed as environment variables:

```bash
AMPLIFY_GITHUB_TOKEN=ghp_xxx \
GOOGLE_CLIENT_ID=xxx \
GOOGLE_CLIENT_SECRET=xxx \
SMOKE_TEST_USER_EMAIL=xxx \
SMOKE_TEST_USER_PASSWORD=xxx \
bash scripts/deploy-local.sh
```

---

## destroy-rc.sh

Destroys a named RC Terraform workspace and deletes it. Intended for cleaning up `rc-*` branch environments, mirroring the `teardown-rc.yml` workflow.

Refuses to target the `default` (production) workspace. Exits cleanly if the workspace does not exist.

```bash
bash scripts/destroy-rc.sh rc-myfeature
```

Secrets are loaded automatically from `scripts/.env.local` if present (only `AMPLIFY_GITHUB_TOKEN` is required).

> **Stale lock:** If a previous `terraform destroy` was interrupted (e.g. a cancelled CI run), the state lock may need manual removal before the script can proceed:
> ```bash
> AWS_PROFILE=sandbox aws s3 rm \
>   s3://sudoku-tf-state/env:/<workspace>/sudoku/terraform.tfstate.tflock
> ```
