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
- ✅ **Already wired** (from the gaps C–F work): the `enable_custom_domain` `workflow_dispatch` input
  is threaded into `deploy-gcp.yml`'s `terraform apply` and hard-set `false` for `rcg-*` pushes; the
  `google_firebase_hosting_custom_domain` + `google_dns_managed_zone.frontend` resources exist,
  count-gated on `local.is_default && var.enable_custom_domain`; `dns_name_servers` output exists.
- ✅ `var.custom_domain = sudoku-gcp.edoatley.co.uk`; prod CORS (`cors_allowed_origins`) already
  includes it.
- ✅ **This PR:** added the `custom_domain_required_dns_records` output — the flattened A/AAAA/TXT
  records Firebase requires *inside* the zone (from the resource's computed `required_dns_updates`),
  the authoritative source PR 3 codifies. (Firebase computes these asynchronously, so the output may
  be `[]` on the first apply and populate on a later refresh.)
- **Acceptance:** `terraform validate`/`fmt` clean; on `workflow_dispatch (workspace=default,
  enable_custom_domain=true)` the custom-domain + managed-zone resources apply and both outputs are
  present (records may lag until Firebase verifies).
- **Depends on:** the C–F stack + bootstrap-script work, merged to `main`.

### PR 2 — Deploy the frontend for `default` (CI) — *step 2*
**Goal:** publish the built SPA to Firebase Hosting for the default site so the host serves the app
(not "Site Not Found").
- ✅ **Already wired:** the `deploy-frontend` job runs for `workspace=default` when a
  `workflow_dispatch` sets `deploy_frontend=true` (for `rcg-*` pushes it keys off repo var
  `GCP_DEPLOY_FRONTEND=true`); `firebase deploy --only hosting` targets the project's default site
  (`<project>.web.app`) via `ui/.firebaserc`.
- ✅ **VITE_* set is complete** — `VITE_API_URL` (prod Cloud Run backend), `VITE_FIREBASE_*`,
  `VITE_AI_COACH`, `VITE_IMAGE_RECOGNITION_URL`, `VITE_MOCK_API=false`, `VITE_DEV_TOOLS` off on
  `default` (CP-GCP-042/043). The only unset `VITE_*` the app reads are AWS-Cognito/dev-only, which
  are correctly absent on the Firebase build.
- ➕ **This PR:** a **fail-loud prerequisite guard** in `deploy-frontend` — abort the build if
  `backend_url` is empty (would bake a hostless `/api/v1`) or `VITE_FIREBASE_API_KEY` is unset (no
  Firebase Auth), rather than silently shipping a broken SPA.
- **Repo config you must set (not code):** add the `VITE_FIREBASE_API_KEY` secret (runbook §3);
  set repo var `GCP_DEPLOY_FRONTEND=true` only if you also want `rcg-*` pushes to deploy the UI.
  Then run `workflow_dispatch` with `workspace=default, deploy_cloud_run=true, deploy_frontend=true`.
- **Acceptance:** `firebase deploy` succeeds; `https://<project>.web.app` serves the SPA (login page
  loads; API calls still CORS-fail from `*.web.app` until the custom domain + invoker — expected,
  since prod CORS allows only `sudoku-gcp.edoatley.co.uk`).
- **Depends on:** PR 1.

### PR 3 — DNS delegation + records + TLS (mostly runbook, some automation) — *step 3*
**Goal:** `https://sudoku-gcp.edoatley.co.uk` resolves to Firebase Hosting with a valid managed cert.
- **NS delegation (scripted, cross-cloud):** run `scripts/infra/gcp/delegate-dns.sh` (added in the
  bootstrap-completeness branch) — reads the Cloud DNS nameservers via `gcloud` and UPSERTs the `NS`
  record in the `edoatley.co.uk` Route53 parent zone. See runbook §6b.
- **In-zone records (scripted):** `scripts/infra/gcp/apply-custom-domain-dns.sh` reads the
  `custom_domain_required_dns_records` output (PR 1) and UPSERTs the A/AAAA/TXT record sets into the
  `sudoku-gcp` zone via `gcloud`. Scripted rather than `google_dns_record_set` because Firebase
  computes the records asynchronously — `for_each` over that unknown value fails the first apply, and
  the exact record/TXT-quoting is best verified against live output. Re-run if the output was empty.
- Firebase issues the managed TLS cert out-of-band (`wait_dns_verification=false`).
- **Acceptance:** `dig sudoku-gcp.edoatley.co.uk` returns Firebase IPs; the domain shows "Connected"
  in the Firebase console; HTTPS serves the SPA.
- **Depends on:** PR 1 (zone + records output), PR 2 (site has content).

### PR 4 — Identity Platform authorized domains + Google sign-in (runbook §4) — *step 4* — **no code**
**Goal:** Google sign-in works on the hosted host.
- ✅ **Already covered — operational, not code.** `scripts/infra/gcp/identity-platform-bootstrap.sh`
  already merges the custom domain into the Identity Platform **authorized domains** (shipped with the
  bootstrap-completeness work); **re-run it** after the domain is live to apply.
- ✅ **No OAuth-client change needed.** The app uses `signInWithPopup` (not `signInWithRedirect`, so no
  Safari third-party-cookie issue), and the handler URI stays
  `https://<project>.firebaseapp.com/__/auth/handler` regardless of the custom domain — so no extra
  redirect URI / JS origin. The only per-host requirement is the authorized-domains entry (above).
  Runbook §4 now states this explicitly to avoid a common setup mistake.
- **Acceptance:** a real Google login completes on `https://sudoku-gcp.edoatley.co.uk` (no
  `auth/unauthorized-domain`).
- **Depends on:** PR 3 (host resolves over HTTPS).

### PR 5 — Prod invoker + end-to-end smoke (step 1 invoker + step 5)
**Goal:** the app is reachable and a smoke test proves it serves.
- **Scripted (runbook §2):** `scripts/infra/gcp/grant-prod-invoker.sh` grants `allUsers`
  `roles/run.invoker` on the prod `sudoku` + `sudoku-image-recognition` services (idempotent, skips
  any not deployed). Kept out of Terraform by design (gap G1).
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
