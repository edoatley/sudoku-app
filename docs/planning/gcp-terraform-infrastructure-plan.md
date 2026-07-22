# Implementation Plan: GCP Infrastructure (infra/gcp)

**Status**: Approved — ready to implement
**Created**: 2026-07-17
**Origin**: `docs/todo/gcp-terraform-infrastructure.md` (follow-up to the `infra/aws/` multi-cloud move, PR #136)
**Arrow**: `cloud-platform` (`docs/arrows/cloud-platform.md`) — GCP facet

---

## 1. Context

The Cloud Platform now targets two clouds. The AWS facet (`infra/aws/`) is complete; this plan
adds the GCP facet (`infra/gcp/`) as **infrastructure scaffolding** — Terraform + bootstrap + a
manual runbook + CI wiring that stand the GCP platform up and keep it `validate`/`plan`-clean.

It deliberately does **not** make the app run end-to-end on GCP. The Java backend persists to
DynamoDB and authenticates against Cognito; the React frontend uses the Cognito/Amplify SDK. Those
app-layer adapters (backend Firestore profile, frontend Firebase Auth, cross-cloud Bedrock wiring)
are **separate future arrows**, not owned here (see HLD *Multi-Cloud Deployment* and the LLD *Scope
of the GCP facet* note).

## 2. Decisions (from the design pass)

| Decision | Choice |
| --- | --- |
| Scope | Infra-only scaffolding; app adapters deferred to per-segment arrows |
| Region | `us-central1` (free-tier coverage); Firestore location `us-central1` |
| Compute | Cloud Run (backend + image-recognition), scale-to-zero |
| Persistence | Firestore (Native), named database per workspace |
| Edge / throttle | Cloud Run direct + in-app JWT; throttle via max-instances × concurrency (accepted deviation from the 25 rps AWS rule — see LLD) |
| Identity | Identity Platform + Google IdP — **manual**, not Terraform |
| IAM / SA / WIF / networking | **Manual** runbook, not Terraform |
| Frontend hosting | Firebase Hosting; deploy after apply |
| DNS | Cloud DNS zone `sudoku-gcp.edoatley.co.uk`; Google-managed TLS |
| Cost | Cloud Billing budget + Pub/Sub (alert-only; hard-cap enforcement deferred) |
| AI | Bedrock cross-cloud via Secret Manager (interim); Vertex AI deferred |
| State / registry | GCS backend `sudoku-tf-state-gcp`; Artifact Registry (`sudoku-backend`, `sudoku-image-recognition`) |

## 3. What "tests-first" means here

Pure infrastructure — no HCL `@spec` unit tests exist (the AWS facet has none either; accepted
gap). Acceptance checks, wired alongside the `.tf`: `terraform fmt -check`, `terraform validate`,
`checkov -d infra/gcp/`, and the CI `terraform-validate` job on `infra/gcp/**`.

## 4. Phased work

1. **Bootstrap** `scripts/infra/gcp-bootstrap.sh` — GCS state bucket, `gcloud services enable`,
   two Artifact Registry repos. No SA/IAM/WIF/Identity Platform.
2. **Runbook** `docs/runbooks/gcp-manual-setup.md` — SAs, IAM bindings, WIF pool/provider,
   Identity Platform + Google IdP + test user, optional VPC. The learning surface.
3. **Terraform** `infra/gcp/` — `terraform.tf` (google + GCS backend + default_labels), `main.tf`,
   `variables.tf`, `outputs.tf`, `cloud_run.tf`, `image_recognition.tf`, `firestore.tf`,
   `firebase_hosting.tf`, `dns.tf`, `budgets.tf`, `README.md`. **No `iam.tf` / `identity_platform.tf`.**
4. **CI + Makefile** — extend `terraform-validate` to `infra/gcp`; `ci.yml` path filter + validate
   job; `ci-deploy.yml` `deploy-gcp` job (WIF auth, plan/apply, `firebase deploy`); teardown paths;
   `Makefile` lint/secure targets.
5. **Coherence** — `terraform init -backend=false && validate && fmt -check`, `checkov`, flip the
   infra-satisfied `CP-GCP-*` specs to `[x]`, update `docs/arrows/index.yaml` + arrow doc.

## 5. Spec ownership within this arrow

Infra-satisfied here (flip to `[x]` on completion): CP-GCP-001..004, 013, 020, 022, 023, 024, 031,
040..043, 050, 051, 060, 070, 080..083.

Remain `[ ]` gaps for future app-layer arrows: CP-GCP-010, 011, 012, 021, 030, 032, 085.

Deferred `[D]`: CP-GCP-061 (budget enforcement), 090 (Vertex AI), 091 (private VPC).

## 6. Out of scope (separate arrows)

- Backend Firestore persistence profile (Game Lifecycle, User Management, League Table, AI Coach).
- Frontend Firebase Auth path (React Frontend, User Management).
- Cross-cloud Bedrock credential wiring (AI Coach, Image Recognition).
- Canonical-`userId` = Google `sub` re-key + DynamoDB→Firestore migration (cross-segment).
