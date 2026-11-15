# Sudoku — GCP Infrastructure (Pulumi)

The GCP facet of the Cloud Platform (`sudoku-eo-2026`, region `us-central1`), at behavioural parity
with `infra/aws/`, managed with **Pulumi** (Python + `uv`). Design:
`docs/llds/cloud-platform-gcp.md`. Runbook: `docs/runbooks/gcp-manual-setup.md`.

## Layout

```text
components/   reusable ComponentResources, shared by both programs
bootstrap/    project, APIs, state bucket, KMS, Artifact Registry, SAs, IAM, WIF, budget
              -> run locally, with human credentials
app/          Firestore, Cloud Run x2, Identity Platform, Hosting, Cloud DNS
              -> run by CI as the WIF-federated deploy service account
tests/        component unit tests against Pulumi's mock runtime (no cloud, no credentials)
```

The two-stack split is the privilege boundary: the CI identity holds no IAM-admin, WIF-admin or
billing permission, so it cannot grant itself anything.

```bash
cd infra/gcp
uv sync                       # once
uv run pytest tests --cov     # component tests — no cloud contact
uv run ruff check . && uv run ruff format --check .
cd app && pulumi preview      # needs credentials + a stack
```

`uv.lock` is committed, so provider versions are pinned in CI.

## Architecture

```text
GitHub ──WIF──> Cloud Run (backend: Quarkus)        ──> Firestore (Native) + Vertex AI (Gemini)
                Cloud Run (image-recognition)       ──> Vertex AI (Gemini Vision)
Firebase Hosting (React SPA) ── custom domain sudoku.gcp.edoatley.co.uk + Google-managed TLS
                                (CNAME in delegated Cloud DNS zone gcp.edoatley.co.uk)
Identity Platform (Google IdP + CI smoke user) ── Pulumi-managed
Cloud Billing budget ── Pub/Sub alerts
```

| AWS                          | GCP                                                   |
| ---------------------------- | ----------------------------------------------------- |
| Lambda                       | Cloud Run (`ContainerService`)                        |
| DynamoDB                     | Firestore Native (`FirestoreDatabase`)                |
| API Gateway + JWT authorizer | Cloud Run direct + in-app JWT                         |
| Cognito                      | Identity Platform (`IdentityPlatform`)                |
| Bedrock (Claude Haiku 4.5)   | Vertex AI Gemini (`gemini-2.5-flash-lite` / `3.8`)    |
| Amplify                      | Firebase Hosting (`StaticSite`)                       |
| Route53 + ACM                | Cloud DNS zone (`DnsZone`) + Google-managed TLS       |
| AWS Budgets                  | Cloud Billing budget + Pub/Sub (`CostGuardrails`)     |
| S3 backend / ECR             | GCS backend (`gs://sudoku-pulumi-state-eo`) / AR      |
| GitHub OIDC → IAM role       | Workload Identity Federation → `sudoku-deploy` SA     |

## Stacks

- `prod` — Production (`main` branch when `DEPLOY_TARGET` is `gcp` or `both`). Uses `(default)`
  Firestore database, `sudoku-eo-2026` Hosting site, and `sudoku.gcp.edoatley.co.uk` custom domain.
- `rcg-*` — Ephemeral per-branch environments. Isolated named Firestore database (`sudoku-<stack>`),
  unique Hosting site (`sudoku-dev-<stack>-<hex>`), and `*-<stack>` Cloud Run services. Destroyed
  automatically on branch deletion via `.github/workflows/teardown-gcp-pulumi.yml`.
