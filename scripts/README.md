# Scripts

Helper scripts for local development, testing, and infrastructure management.

## Directory structure

```
scripts/
├── local/       — Local dev and test scripts (run on your machine)
├── infra/       — Infrastructure setup and deployment scripts
├── github/      — CI/CD scripts called by GitHub Actions workflows
└── logs/        — Ad-hoc log retrieval and analysis tools
```

### The two `.env.local` files (don't confuse them)

| File | Purpose | Contents | Created by |
|---|---|---|---|
| `scripts/.env.local` | **Infra/deploy secrets** — sourced automatically by the shell scripts here (deploy, destroy, GCP identity bootstrap). Server-side only. | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `AMPLIFY_GITHUB_TOKEN`, `SMOKE_TEST_USER_*` | `setup-local-secrets.sh` |
| `ui/.env.local` | **Frontend build/runtime** config for Vite (`npm run dev`/`build`). Values are shipped to the browser. | `VITE_*` only (`VITE_API_URL`, `VITE_FIREBASE_*`, `VITE_IMAGE_RECOGNITION_URL`, `VITE_AI_COACH`, …) | by hand |

Run `bash scripts/infra/setup-local-secrets.sh` once to create `scripts/.env.local`. It
auto-detects `GOOGLE_CLIENT_ID`/`SECRET` from the Terraform state (see the tip under
`setup-local-secrets.sh` below), so you can usually just press Enter.

---

## scripts/local/

### local-dev.sh

Starts the full local development stack with hot-reload for both backend and UI:

- **LocalStack** (Docker) on port 4566 — provides DynamoDB Local
- **Quarkus backend** (`mvn quarkus:dev`) on port 8080 — hot-reloads Java changes; `DevDatabaseInitializer` creates the DynamoDB tables automatically on first startup
- **Vite UI dev server** (`npm run dev`) on port 5174 — hot-reloads JS/JSX changes

Press **Ctrl-C** to cleanly shut down all three processes.

```bash
bash scripts/local/local-dev.sh
```

Once the stack is up, run the Playwright hint-demo integration tests against it in a separate terminal:

```bash
cd ui && npm run test:hint-demos
```

Prerequisites: `docker`, `java`, `node`, `npm`.

---

### local-alltests.sh

Runs all test suites locally, mirroring the CI PR pipeline:

- Image Recognition (pytest)
- Frontend Lint (ESLint)
- Frontend Security Audit (npm audit)
- Frontend E2E (Playwright)
- Backend (Maven verify + JaCoCo, with DynamoDB Local)
- Integration (Docker Compose + Playwright)
- Infra (Terraform fmt + validate)

```bash
bash scripts/local/local-alltests.sh

# Skip individual suites:
bash scripts/local/local-alltests.sh --skip-e2e --skip-infra
```

Available flags: `--skip-image-recognition`, `--skip-lint`, `--skip-audit`, `--skip-e2e`,
`--skip-backend`, `--skip-integration`, `--skip-infra`.

Exits 0 if all suites pass, 1 if any fail.

Prerequisites: `docker`, `java`, `node`, `python3`, `terraform`, `aws`.

---

### local-smoke-test.sh

Runs the full smoke test suite locally against the deployed environment, mirroring the
`smoke-tests.yml` CI workflow: API smoke tests, image recognition, and Playwright integration
tests. Resolves all required values (API URL, Cognito client ID, tokens) automatically from
Terraform outputs and `.env.local`.

```bash
AWS_PROFILE=sandbox bash scripts/local/local-smoke-test.sh             # current branch
AWS_PROFILE=sandbox bash scripts/local/local-smoke-test.sh rc-myfeature

# Skip individual test types:
AWS_PROFILE=sandbox bash scripts/local/local-smoke-test.sh --skip-playwright
AWS_PROFILE=sandbox bash scripts/local/local-smoke-test.sh --skip-image --skip-api
```

Prerequisites: `aws` CLI with sandbox profile, `terraform`, `node`, `jq`.

---

### smoke-token-local.sh

Validates Cognito token acquisition locally using the `sudoku-smoke-test-rc` app client.
Useful for debugging smoke test authentication failures without running the full CI pipeline.

```bash
AWS_PROFILE=sandbox bash scripts/local/smoke-token-local.sh <user-pool-id> <username> <password>
```

Example:

```bash
AWS_PROFILE=sandbox bash scripts/local/smoke-token-local.sh eu-west-2_71X75OgH8 user@example.com MyP@ss
```

Prints truncated `IdToken` and `AccessToken` values on success (first 40 characters only, safe to share in logs).

---

## scripts/infra/

### setup-local-secrets.sh

**Run once** to populate `scripts/.env.local` with the secrets needed by the local deploy and
destroy scripts. Prompts for each value interactively (input is not echoed). Re-running lets
you update individual values while keeping the rest.

The generated file is `chmod 600` and git-ignored.

```bash
bash scripts/infra/setup-local-secrets.sh
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
>
> **Tip:** `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` are auto-detected by the script from the same
> state (the Cognito Google provider stores them there in plaintext; `describe-identity-provider`
> masks the secret and `gcloud` has no API for OAuth clients, so the state is the CLI source). To
> read them directly:
> ```bash
> AWS_PROFILE=sandbox aws s3 cp s3://sudoku-tf-state/sudoku/terraform.tfstate - \
>   | jq -r '.resources[] | select(.type=="aws_cognito_identity_provider")
>            | .instances[].attributes.provider_details | "\(.client_id)\t\(.client_secret)"' | head -1
> ```

---

### bootstrap.sh

**Run once** before the first Terraform apply. Creates the AWS prerequisites that Terraform
itself cannot manage (chicken-and-egg):

- S3 bucket for Terraform state (`sudoku-tf-state`)
- GitHub Actions OIDC provider
- IAM role `sudoku-github-actions-deploy` with the inline deploy policy (includes ECR, Lambda, API Gateway, IAM, DynamoDB, CloudWatch permissions)
- ECR repository `sudoku-image-recognition` for the image recognition Lambda container image

Safe to re-run — all steps are idempotent. Re-running updates the trust policy and inline
policy in-place, which is how IAM permission changes are applied (e.g. after adding new
actions to the policy).

```bash
bash scripts/infra/bootstrap.sh
```

---

### deploy-local.sh

Runs a full Terraform plan + apply locally, mirroring the `deploy` job in the CI workflows.
Skips the Amplify build trigger and smoke tests — infrastructure only.

Automatically:
- Resolves the Terraform workspace from the branch name (`main` → `default`/prod, `rc-*` → named workspace)
- Builds the Lambda zip via Maven if not present locally and not found in S3
- Pauses for confirmation before applying the plan
- Tightens CORS and Cognito callback URLs after apply (same post-apply steps as CI)

```bash
# Deploy current git branch (default)
bash scripts/infra/deploy-local.sh

# Deploy a specific branch
bash scripts/infra/deploy-local.sh main
bash scripts/infra/deploy-local.sh rc-myfeature
```

Secrets are loaded automatically from `scripts/.env.local` if present (run `setup-local-secrets.sh`
first), or can be passed as environment variables:

```bash
AMPLIFY_GITHUB_TOKEN=ghp_xxx \
GOOGLE_CLIENT_ID=xxx \
GOOGLE_CLIENT_SECRET=xxx \
SMOKE_TEST_USER_EMAIL=xxx \
SMOKE_TEST_USER_PASSWORD=xxx \
bash scripts/infra/deploy-local.sh
```

To also update the image recognition Lambda, pass the ECR image URI:

```bash
IMAGE_RECOGNITION_IMAGE_URI=123456789.dkr.ecr.eu-west-2.amazonaws.com/sudoku-image-recognition:latest \
bash scripts/infra/deploy-local.sh
```

If `IMAGE_RECOGNITION_IMAGE_URI` is unset, the image recognition Lambda is not updated
(useful when only testing backend or infra changes locally).

---

### destroy-rc.sh

Destroys a named RC Terraform workspace and deletes it. Intended for cleaning up `rc-*`
branch environments, mirroring the `teardown-rc.yml` workflow.

Refuses to target the `default` (production) workspace. Exits cleanly if the workspace
does not exist.

```bash
bash scripts/infra/destroy-rc.sh rc-myfeature
```

Secrets are loaded automatically from `scripts/.env.local` if present (only
`AMPLIFY_GITHUB_TOKEN` is required).

> **Stale lock:** If a previous `terraform destroy` was interrupted (e.g. a cancelled CI run),
> the state lock may need manual removal before the script can proceed:
> ```bash
> AWS_PROFILE=sandbox aws s3 rm \
>   s3://sudoku-tf-state/env:/<workspace>/sudoku/terraform.tfstate.tflock
> ```

---

### delegate-dns.sh

Creates an NS delegation record in the `edoatley.co.uk` hosted zone (default AWS account)
pointing to a subdomain zone managed in the sandbox account. Run **once per subdomain** after
`terraform apply` outputs the nameservers.

```bash
PARENT_ZONE_ID=Z0123456789ABCDEF \
bash scripts/infra/delegate-dns.sh [--subdomain <name>] ns1 ns2 ns3 ns4
```

Get the nameservers from Terraform:

```bash
cd infra/aws && AWS_PROFILE=sandbox terraform output subdomain_nameservers
```

For the **GCP** frontend domain, use the wrapper `gcp-delegate-dns.sh` instead — it reads the Cloud
DNS nameservers itself and calls the above for the Route53 side (see the GCP runbook §6b):

```bash
PARENT_ZONE_ID=Z0123456789ABCDEF PROJECT_ID=<gcp-project-id> \
bash scripts/infra/gcp-delegate-dns.sh
```

---

### test-budget-deny.sh

Verifies the AWS Budgets hard-cap mechanism end-to-end without incurring any real spend.
Manually attaches the `SudokuBedrockDeny` IAM policy to `SudokuLambdaExecRole`, confirms
Bedrock is blocked via `aws iam simulate-principal-policy`, then detaches the policy and
confirms access is restored. Requires the `default` Terraform workspace to be applied with
`budget_alert_email` set (so the deny policy exists in the account).

```bash
AWS_PROFILE=sandbox bash scripts/infra/test-budget-deny.sh
```

---

### bedrock_quota_report.sh

Reports AWS Bedrock service quotas and recent CloudWatch usage for the models used by the
Sudoku image recognition service (Nova Pro and Nova Lite, `eu-west-2`). Useful for diagnosing
throttling issues or before requesting quota increases.

```bash
bash scripts/infra/bedrock_quota_report.sh

# Print CLI commands to request a quota increase:
bash scripts/infra/bedrock_quota_report.sh --request-increase

# Request increase for a specific quota code:
bash scripts/infra/bedrock_quota_report.sh --request-increase L-XXXX
```

Prerequisites: `aws` CLI with sandbox profile, `jq`.

---

## scripts/github/

Scripts called by GitHub Actions workflows. Can also be run locally for debugging.

### github/resolve-environment.sh

Derives the Terraform workspace, environment name, and `is_main` flag from the current git
branch. Writes to `$GITHUB_OUTPUT` in CI; prints `KEY=VALUE` pairs locally.

```bash
bash scripts/github/resolve-environment.sh
```

---

### github/terraform-plan.sh

Runs a parameterised `terraform plan`. Must be called after `terraform init` and workspace
selection. Used by the `ci-deploy.yml` workflow.

```bash
bash scripts/github/terraform-plan.sh \
  --is-main true \
  --out tfplan
```

---

### github/amplify-pre-deploy.sh

Pre-Terraform deployment helper for RC branches — releases the shared
`sudoku-beta.edoatley.co.uk` domain from whichever Amplify app currently holds it, so the
current workspace can claim it in a single `terraform apply`. Used by `ci-deploy.yml`.

---

### github/amplify-post-deploy.sh

Post-Terraform deployment helper — tightens API Gateway CORS, updates Cognito callback URLs,
polls for Amplify domain association verification, and triggers Amplify builds when needed.
Used by `ci-deploy.yml`.

---

### github/api-smoke-tests.sh

Runs a suite of HTTP smoke tests against the deployed API Gateway to verify the core
endpoints are responding correctly.

```bash
bash scripts/github/api-smoke-tests.sh <api-gateway-url>
```

Exit code: 0 if all tests pass, 1 if any fail. Appends a Markdown summary to
`$GITHUB_STEP_SUMMARY` when run in CI.

---

### github/image-smoke-test.sh

Posts a base64-encoded puzzle image to `/api/v1/ai/image-to-puzzle` and asserts a 200 response
with a 9-row grid. Used by the `smoke-tests.yml` workflow; also runnable locally against any
deployed environment.

```bash
# 1. Acquire a token (see smoke-token-local.sh above, or use the full flow below)
ID_TOKEN=$(AWS_PROFILE=sandbox aws cognito-idp initiate-auth \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id <client-id> \
  --auth-parameters USERNAME=<email>,PASSWORD=<password> \
  --query 'AuthenticationResult.IdToken' --output text)

# 2. Run the smoke test
bash scripts/github/image-smoke-test.sh \
  https://<api-id>.execute-api.eu-west-2.amazonaws.com \
  image_recognition/tests/fixtures/puzzle_1.jpeg \
  "${ID_TOKEN}"
```

The token can also be supplied via the `SMOKE_ID_TOKEN` environment variable instead of the
third argument.

---

### github/coach-smoke-test.sh

Posts a fixed naked-single board to `/api/v1/ai/coach` with a distinctive marker in
`userMessage`, asserts a 200 response with a non-blank `aiMessage`, then polls CloudWatch
Logs for the matching `COACH_REQUEST`/`COACH_RESPONSE` pair (correlated by `cid`) and asserts
the logged content — `userMessage`, `board`, `candidatesGrid`, `aiMessage` — matches what was
actually sent/received. RC-only (coach is disabled on `main`/`default`). Used by the
`smoke-tests.yml` workflow; also runnable locally against any RC-deployed environment.

```bash
AWS_PROFILE=sandbox bash scripts/github/coach-smoke-test.sh \
  https://<api-id>.execute-api.eu-west-2.amazonaws.com \
  rc-ai-coach-improvements-1 \
  "${ID_TOKEN}"
```

Requires the CI/deploy role to have `logs:FilterLogEvents`/`logs:GetLogEvents` (see
`scripts/infra/bootstrap.sh`). Tunable via `COACH_LOG_POLL_ATTEMPTS` (default 6) and
`COACH_LOG_POLL_DELAY` (default 5s) if CloudWatch ingestion lag needs more headroom.

Retries the coach call itself on 500/503 (`COACH_HTTP_RETRY_ATTEMPTS`, default 2;
`COACH_HTTP_RETRY_DELAY`, default 3s) — the coach's Bedrock client can be genuinely cold on
its first-ever invocation after a fresh deploy (the existing SnapStart warm-up only primes
the HTTP layer, not the Bedrock SDK client), occasionally exceeding the Lambda's 8s timeout
on that first call.

---

## scripts/logs/

### download-coach-logs.sh

Downloads AI coach interaction logs (`COACH_REQUEST`/`COACH_RESPONSE` structured JSON lines,
including full conversation content — user message, AI reply, board, candidates) from CloudWatch
for a given workspace, for manual review of coaching quality.

```bash
AWS_PROFILE=sandbox bash scripts/logs/download-coach-logs.sh --hours 1

# Reconstruct paired turns (prompt + reply), joined by cid:
AWS_PROFILE=sandbox bash scripts/logs/download-coach-logs.sh --hours 1 | \
  jq -s 'group_by(.cid)[] | {cid: .[0].cid, userMessage: .[0].userMessage,
         aiMessage: (map(select(.type=="COACH_RESPONSE"))[0].aiMessage)}'
```

`--workspace` defaults to the current git branch's Terraform workspace (same sanitize/truncate
rule as `resolve-environment.sh` — `main` maps to `default`); pass it explicitly to target a
different workspace than the one you're currently on.

Prerequisites: `aws` CLI, `jq`.
