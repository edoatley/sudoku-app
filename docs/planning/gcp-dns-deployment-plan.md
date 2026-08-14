# Plan: deploy GCP to a real DNS host (`sudoku-gcp.edoatley.co.uk`)

Sequenced follow-up to the C–F parity work (PR #163). Goal: take the now-working GCP runtime slice
from ephemeral `rcg-*` workspaces (tested with a **local** UI) to a **hosted, DNS-addressable** prod
deployment on `sudoku-gcp.edoatley.co.uk`, reachable and usable end-to-end in a browser.

This is the remaining **gap G** ("deployment-target readiness") from `docs/todo/gcp-aws-parity.md`,
broken into five independently-reviewable PRs. Manual console/DNS steps live in
`docs/runbooks/gcp-manual-setup.md`; each PR below says which runbook section it automates or invokes.

## Context that shapes the plan
- **The custom domain is default-workspace only.** `google_firebase_hosting_custom_domain` and the
  `sudoku-gcp` Cloud DNS zone are gated on `local.is_default && var.enable_custom_domain`
  (`infra/gcp/{firebase_hosting,dns}.tf`). `enable_custom_domain` defaults to **`false`**.
- **Prod deploy is manual.** `deploy-gcp.yml` auto-runs only on `rcg-*` push; the `default` workspace
  is a `workflow_dispatch` (inputs: `workspace=default`, `apply_backend`, `deploy_image_recognition`,
  …).
- **Hosted UI → backend is cross-origin.** `ui/firebase.json` has SPA fallback only (no `/api`
  rewrite), and `deploy-gcp.yml` sets `VITE_API_URL=<cloud-run-backend>/api/v1`. So the browser calls
  Cloud Run directly and **CORS must allow the origin**. Default-workspace CORS is already
  `https://${custom_domain},http://localhost:5173` (`main.tf`) — i.e. it allows the **custom domain**
  but *not* the raw `*.web.app`. Test via the custom domain.
- **Prod invoker stays manual by design** (`allUsers` `roles/run.invoker`, runbook §2) — the app
  enforces auth in-app, so this only lets requests *reach* Cloud Run.

## PR sequence

### PR 1 — Provision the custom domain + Cloud DNS zone (IaC) — *step 1/3 infra*
**Goal:** make `terraform apply` on `default` create the Firebase custom-domain resource and the
`sudoku-gcp` Cloud DNS managed zone, and surface the DNS records to add.
- Turn on `enable_custom_domain` for the default workspace — via a `default.tfvars` /
  `workflow_dispatch` input threaded into `deploy-gcp.yml`, keeping the var `false` elsewhere.
- Confirm `var.custom_domain = sudoku-gcp.edoatley.co.uk` and that prod CORS
  (`cors_allowed_origins`) already includes it (it does).
- Add a terraform output for the `google_firebase_hosting_custom_domain` **required DNS records**
  (A/AAAA + TXT) so PR 3 has an authoritative source.
- **Acceptance:** `workflow_dispatch (workspace=default, apply_backend=true)` applies clean; the
  custom-domain + managed-zone resources exist; the required-records output is populated.
- **Depends on:** #163 stack merged to `main`.

### PR 2 — Deploy the frontend for `default` (CI) — *step 2*
**Goal:** publish the built SPA to Firebase Hosting for the default site so the host serves the app
(not "Site Not Found").
- Wire the `deploy_frontend` job to run for `workspace=default` (today it needs repo var
  `GCP_DEPLOY_FRONTEND=true` + the `VITE_FIREBASE_API_KEY` secret — see runbook §3).
- Ensure the build injects the full prod `VITE_*` set (`VITE_API_URL` = prod Cloud Run backend,
  `VITE_FIREBASE_*`, `VITE_AI_COACH`, `VITE_IMAGE_RECOGNITION_URL`) — item F / CP-GCP-042/043.
- **Repo config (not code, call out in PR):** set `GCP_DEPLOY_FRONTEND=true`, add
  `VITE_FIREBASE_API_KEY` secret.
- **Acceptance:** `firebase deploy` succeeds; `https://<project>.web.app` serves the SPA (login page
  loads; API calls will still 403/CORS-fail until PR 3 + invoker — expected).
- **Depends on:** PR 1.

### PR 3 — DNS delegation + records + TLS (mostly runbook, some automation) — *step 3*
**Goal:** `https://sudoku-gcp.edoatley.co.uk` resolves to Firebase Hosting with a valid managed cert.
- **Manual, one-time (runbook §, DNS):** delegate `sudoku-gcp.edoatley.co.uk` from the parent
  `edoatley.co.uk` zone via NS records to the `sudoku-gcp` Cloud DNS zone's nameservers.
- Add the Firebase-required **A/AAAA + TXT verification** records (from PR 1's output) to the
  `sudoku-gcp` zone — ideally as `google_dns_record_set` resources so they're codified, not manual.
- Firebase issues the managed TLS cert out-of-band (`wait_dns_verification=false`).
- **Acceptance:** `dig sudoku-gcp.edoatley.co.uk` returns Firebase IPs; the domain shows "Connected"
  in the Firebase console; HTTPS serves the SPA.
- **Depends on:** PR 1 (zone + records output), PR 2 (site has content).

### PR 4 — Identity Platform authorized domains + Google sign-in (runbook §4) — *step 4*
**Goal:** Google sign-in works on the hosted host.
- Add `sudoku-gcp.edoatley.co.uk` (and any `*.web.app` used) to Identity Platform **authorized
  domains** and the Google OAuth client's authorized JS origins / redirect URIs.
- Prefer scripting via `scripts/infra/gcp-identity-platform-bootstrap.sh` (extend to merge the prod
  host) over pure console clicks; document the manual fallback in runbook §4.
- **Acceptance:** a real Google login completes on `https://sudoku-gcp.edoatley.co.uk` (no
  `auth/unauthorized-domain`).
- **Depends on:** PR 3 (host resolves over HTTPS).

### PR 5 — Prod invoker + end-to-end smoke (step 1 invoker + step 5)
**Goal:** the app is reachable and a smoke test proves it serves.
- **Manual (runbook §2):** grant `allUsers` `roles/run.invoker` on the prod backend (and image-rec)
  Cloud Run services. Keep prod manual (not Terraform) per current policy; document clearly.
- Add an automated **post-deploy smoke** (G6): mint a token with `scripts/github/gcp-smoke-token.sh`
  (CP-GCP-032) and assert `GET /players/me` 200 + `POST /games` 201 against the prod backend, wired
  as a `deploy-gcp.yml` step (or a `workflow_dispatch` job) so a deploy isn't "green" until the env
  actually serves.
- **Acceptance:** browser flow on the custom domain (Google sign-in → create game → resume) works;
  the CI smoke step passes.
- **Depends on:** PR 2–4.

## Order & parallelism
```
#163 stack ──▶ PR1 (domain+zone IaC) ──▶ PR2 (frontend deploy)
                        └────────────────▶ PR3 (DNS+TLS) ──▶ PR4 (IdP domains) ──▶ PR5 (invoker+smoke)
```
PR1 and PR2 can overlap; PR3 needs both; PR4/PR5 are sequential after PR3.

## Deferred / out of scope (not required for the DNS host)
- **Same-origin `/api` Hosting rewrite** — would drop the cross-origin CORS dependency; nice-to-have.
- **Budget hard-cap** (CP-GCP-061, Pub/Sub-triggered function).
- **Per-workspace hosted UIs** for `rcg-*` (G3) — this plan targets the default/prod host only.
- **Custom domain for the backend** — not needed; the SPA calls the Cloud Run URL via `VITE_API_URL`.

## References
`docs/todo/gcp-aws-parity.md` (gap G), `docs/runbooks/gcp-manual-setup.md` (§2 invoker, §3 CI
secrets, §4 Identity Platform), `docs/aws-vs-gcp-comparison.md`, `infra/gcp/{firebase_hosting,dns,
main}.tf`, `infra/gcp/variables.tf` (`custom_domain`, `enable_custom_domain`), `.github/workflows/
deploy-gcp.yml`. Specs: CP-GCP-014 (invoker), CP-GCP-032 (smoke user), CP-GCP-042/043 (VITE wiring).
