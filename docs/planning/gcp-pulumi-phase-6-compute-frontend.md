# Implementation Plan: GCP Pulumi — Phase 6, Compute and Frontend

**Status**: Complete — 2026-09-16
**Created**: 2026-09-16
**Origin**: `docs/planning/gcp-pulumi-replatform.md` §4, `gcp-pulumi-handoff.md` §4
**Arrow**: `cloud-platform` (`docs/arrows/cloud-platform.md`) — GCP facet

---

## 1. Goal

A full end-to-end environment on `sudoku-eo-2026`, built entirely by Pulumi and proven on an
ephemeral `rcg-*` stack before anything prod-shaped is attempted. Both Cloud Run services healthy,
a real Google sign-in working against the RC Hosting URL, an image import succeeding — and **no
AWS credential anywhere in either service's environment**.

The old project `sudoku-app-eo` keeps serving production throughout. Nothing in this phase points
at it, and its Terraform path stays runnable.

## 2. Coherence pre-flight

Four findings from reading the arrow before starting. Three are drift in the LLD; one is a
spec-marker bookkeeping note.

| # | Finding | Fix |
| --- | --- | --- |
| C1 | `cloud-platform-gcp.md` § AI Inference names `gemini-2.5-flash` for image recognition. Phase 5 measured 2.5-flash at 92.6% / 0-of-5 and landed on **`gemini-3.8-flash`** (100% / 5-of-5). | Correct the LLD to `gemini-3.8-flash`. |
| C2 | `cloud-platform-gcp.md` § Compute lists `VERTEX_MODELS` as an image-recognition env var. `providers/vertex.py` already defaults to the accuracy-gated model; setting it in the stack creates a second place to change it. | Drop `VERTEX_MODELS` from the LLD env list (see D2). |
| C3 | The same § reads as though image recognition inherits the backend's `GCP_REGION`. It must not (see D1). | Split the two env lists explicitly in the LLD. |
| C4 | The handoff says to flip `CP-GCP-001..004`, `-010..013`, `-040..043`. All of those are already `[x]` — they were satisfied by the Terraform facet on the old project and the re-platform does not regress them. Only **`CP-PUL-020`** and **`CP-PUL-023`** are `[ ]`. | Flip the two. Leave the rest alone. |

No HLD change: this phase realises the architecture already described, it does not alter it.
No new EARS IDs: `CP-PUL-020` and `CP-PUL-023` already say exactly what this phase implements.

## 3. Decisions this phase must make

### D1 — `GCP_REGION` is a backend-only variable *(trap)*

`image_recognition/providers/vertex.py` reads `GCP_REGION` as the **Vertex endpoint location**,
defaulting to `global`. `gemini-3.8-flash` is served *only* from `global`; a regional endpoint
returns a 404 whose message blames the model name. Setting `GCP_REGION=us-central1` on the
image-recognition service would therefore break every recognition request, with an error that
reads like a model or entitlement problem.

The backend has the opposite need: `coach.vertex.location=${GCP_REGION:us-central1}` with
`gemini-2.5-flash-lite`, which is regional.

**Decision:** set `GCP_REGION` on the backend only; omit it from image recognition so the
provider's `global` default stands. A unit test asserts its absence, because nothing else would
catch the regression until a live image import failed.

*Alternative considered:* rename the image-recognition variable to `VERTEX_LOCATION`. Rejected —
it changes Phase-5 application code that is already accuracy-gated and passing, for no benefit
this phase needs.

### D2 — `VERTEX_MODELS` stays unset

`DEFAULT_MODEL` in `providers/vertex.py` is `gemini-3.8-flash`, the value the accuracy gate
actually cleared, with the measurement recorded next to it. Pinning the same string in the Pulumi
stack duplicates it. Leave it unset; the env var remains available as an override.

*Alternative considered:* pin it in IaC, mirroring Terraform's `BEDROCK_MODELS`. Rejected — that
precedent existed because the Bedrock model list was an infrastructure concern (cross-cloud
region, inference profiles). The Vertex model is an application concern.

### D3 — A service is created only when its image tag is configured

The prod `app` stack today holds 16 resources and no image tags, and `pulumi up` on it must keep
reporting no changes. So `ContainerService` is instantiated only when
`sudoku:backendImageTag` / `sudoku:imageRecognitionImageTag` is set. CI always sets both.

This is presence-of-an-artefact, not a feature toggle: it cannot silently revert a setting the way
the `workflow_dispatch` booleans Phase 7 deletes could. The outputs `backend_url` and
`image_recognition_url` are `None` when the corresponding service is absent, and the frontend job
fails loud on an empty backend URL exactly as it does today.

### D4 — Hosting site is selected by `firebase.json`'s `site` key, not a deploy target

Gap G3 is that RC frontends had a Hosting site but no deploy target, so `firebase deploy` went to
the project default site. The handoff suggests `firebase target:apply`. That writes into
`ui/.firebaserc` and requires `ui/firebase.json` to carry a `hosting.target` key — which would
break the **old** project's still-live `deploy-gcp.yml` frontend job, whose `deploy --only hosting`
would then refuse to run without an applied target.

**Decision:** the new workflow patches `"site": "<site_id>"` into a copy of `ui/firebase.json`
before deploying. One key, no `.firebaserc` mutation, no effect on the incumbent path, and the
site is named explicitly — which is what G3 actually asks for.

### D5 — A new workflow, rather than converting `deploy-gcp.yml` *(needs your call — see §7)*

`deploy-gcp.yml` is the live Terraform path to the **old** project: `rcg-*` push for RC, and
`workflow_dispatch` for production. Converting it to Pulumi now would leave no way to redeploy
GCP production before the Phase 8 cutover.

**Proposed:** add `.github/workflows/deploy-gcp-pulumi.yml` (`rcg-*` push + `workflow_dispatch`),
and remove *only* the `rcg-*` push trigger from `deploy-gcp.yml` so the two cannot race. The old
workflow stays dispatch-only until Phase 7 folds this one into `ci-deploy.yml` and Phase 9 deletes
it. `teardown-gcp.yml` likewise loses its `rcg-*` reach this phase (its `terraform destroy` would
target a workspace that no longer exists); Pulumi teardown is driven by hand in 6e and automated
in Phase 7.

### D6 — The project id is a repo variable named `GCP_PULUMI_PROJECT_ID`

The LLD's end-state is `GCP_PROJECT_ID` as a repository **variable** rather than a secret: it is
not sensitive — it appears in every Cloud Run URL, in the Firebase config the SPA ships, and
already in committed config (`app/Pulumi.prod.yaml`, `PULUMI_KMS_KEY`, `PULUMI_STATE_BUCKET`) —
and making it a secret is what forced the current workflow to pass image *tags* between jobs and
reassemble URIs in-job.

That name is taken. `GCP_PROJECT_ID` is a **secret** holding the **old** project id, and
`deploy-gcp.yml` still reads it (D5). A same-named variable would put `vars.GCP_PROJECT_ID` (new
project) and `secrets.GCP_PROJECT_ID` (old project) beside each other in two live workflows.

**Decision:** `GCP_PULUMI_PROJECT_ID = sudoku-eo-2026`, matching the `PULUMI_STATE_BUCKET` /
`PULUMI_KMS_KEY` variables that already name the new project openly. Phase 9 collapses it to
`GCP_PROJECT_ID` once the Terraform path and its secrets are deleted.

**Set 2026-09-16.** Verified against `gcloud projects describe sudoku-eo-2026` (project number
`633423842545`, ACTIVE) and the committed `sudoku:projectId`.

The rest of the `rcg-*` credential chain already exists under the `_NEXT` convention and needs no
action: `GCP_WIF_PROVIDER_NEXT`, `GCP_DEPLOY_SA_EMAIL_NEXT`, `GCP_FIREBASE_API_KEY_NEXT`,
`SMOKE_TEST_USER_PASSWORD_NEXT`. **Never overwrite the un-suffixed originals** — they serve AWS
Cognito and the old GCP project. Phase 8 does the rename.

## 4. Environment matrix

Every value comes from a stack output or `naming.py`. Nothing is hand-typed twice.

**Backend** — `sudoku{suffix}`, max instances 4, concurrency 40, timeout 60s

| Variable | Source |
| --- | --- |
| `QUARKUS_PROFILE` | `gcp` |
| `QUARKUS_CONFIG_PROFILE_PARENT` | `prod` |
| `GCP_PROJECT_ID` | bootstrap `project_id` |
| `QUARKUS_GOOGLE_CLOUD_PROJECT_ID` | bootstrap `project_id` |
| `CORS_ALLOWED_ORIGINS` | `Output.all` over the Hosting origin / custom domain + `localhost:5173` |
| `COACH_AI_PROVIDER` | `vertex` |
| `GCP_REGION` | stack `region` |

**Image recognition** — `sudoku-image-recognition{suffix}`, max instances 2, concurrency 4, timeout 60s

| Variable | Source |
| --- | --- |
| `GCP_PROJECT_ID` | bootstrap `project_id` |
| `CORS_ALLOWED_ORIGINS` | same `Output` as the backend |
| `IMAGE_AI_PROVIDER` | `vertex` |

No `AWS_*`, no `BEDROCK_*`, no `COACH_BEDROCK_API_MODE`, no Secret Manager reference on either
service. `test_no_aws_credentials_are_injected` already guards the component; this phase adds the
same assertion at the stack level.

## 5. Work items

- [x] **6a. Wire `ContainerService` twice** in `app/__main__.py`, per §4, conditional on image
      tags (D3). Export `backend_url` and `image_recognition_url`.
      @spec CP-GCP-001, CP-GCP-002, CP-GCP-003, CP-GCP-004, CP-GCP-013, CP-PUL-020, CP-PUL-023
- [x] **6b. CORS inside the program** — `Output.all(...)` over the Hosting origin (prod: the
      custom domain) plus `localhost:5173`, resolved before the backend is constructed. The
      post-apply step the AWS facet needs does not exist here.
      @spec CP-GCP-012
- [x] **6c. Build both images to the new Artifact Registry.** The project id becomes a repo
      **variable** (D6), which removes the tag-only-crosses-jobs workaround — jobs can pass full
      image URIs again.
- [x] **6d. Frontend build + deploy** from stack outputs `firebase_api_key`,
      `firebase_auth_domain`, `hosting_site_id`, `backend_url`, `image_recognition_url`, with
      `VITE_AUTH_PROVIDER=firebase`.
      @spec CP-GCP-041, CP-GCP-042, CP-GCP-043
- [x] **6e. Ephemeral `rcg-*` stack** — create, deploy, smoke, `pulumi destroy`, `pulumi stack rm`.
      **`--force` is prohibited**; it orphans live resources.
- [x] **6f. Target the Hosting site explicitly** per D4 — closes gap G3.
- [x] **6g. Delete `scripts/github/gcp-workspace-name.sh`.** `components/naming.py` is
      authoritative. Its only remaining caller is `deploy-gcp.yml`'s push path, which D5 removes.
      One doc reference in `docs/aws-vs-gcp-deployment.md` updates with it.
      @spec CP-PUL-021
- [x] **6h. Unit tests** (written first) — §6.
- [x] **6i. Doc cascade** — LLD fixes C1–C3; flip `CP-PUL-020` and `CP-PUL-023`; update the
      handoff and the umbrella plan's phase table.

## 6. Tests, written before the code

All in `infra/gcp/tests/`, run by `ci-pulumi` with no cloud contact.

| Test | Asserts |
| --- | --- |
| `test_backend_env_is_complete` | all seven backend variables present, values from outputs |
| `test_image_recognition_env_has_no_gcp_region` | **D1** — the 404 trap cannot be reintroduced |
| `test_image_recognition_selects_vertex` | `IMAGE_AI_PROVIDER=vertex`, no `BEDROCK_*` |
| `test_neither_service_receives_aws_credentials` | stack-level `CP-GCP-089` guard |
| `test_cors_origins_resolve_from_hosting` | prod → custom domain; rcg → `*.web.app`; both keep localhost |
| `test_services_are_skipped_without_an_image_tag` | **D3** — prod's tag-less stack still previews clean |
| `test_both_services_get_the_public_invoker` | `CP-PUL-023` |
| `test_image_uri_is_built_from_the_registry_output` | no hand-assembled registry host |

Runtime acceptance is 6e: `/api/v1/q/health/ready` 200 on both services, a real sign-in on the RC
Hosting URL, one successful image import, and `gcloud run services describe` showing zero `AWS_*`
variables.

## 7. Definition of Done

1. `uv run ruff check infra/gcp` and `uv run pytest infra/gcp/tests` green.
2. `pulumi preview` on the **prod** app stack reports **no changes** — this phase must not touch
   production.
3. An `rcg-*` stack deploys both services and a frontend; sign-in, a game, and an image import all
   work against it.
4. `gcloud run services describe` on both RC services shows no `AWS_*` variable.
5. `pulumi destroy` + `pulumi stack rm` leave zero residue (`make gcp-inventory` clean).
6. `deploy-gcp.yml` still applies the **old** project by dispatch.
7. `CP-PUL-020` and `CP-PUL-023` flipped to `[x]`; LLD findings C1–C3 corrected.

## 8. Open question for the user

**D5** is the only genuine fork: a new workflow alongside the Terraform one (proposed), versus
converting `deploy-gcp.yml` in place and accepting that GCP production cannot be redeployed
between now and the Phase 8 cutover. Everything else in this plan is a recommendation I am
confident in.

## 9. Outcome — verified 2026-09-17

Deployed to stack `rcg-phase6` by `deploy-gcp-pulumi.yml`, run 35125095131, all five jobs green.

| Claim | Evidence |
| --- | --- |
| Both services serve | `/api/v1/q/health/ready` 200, `/players/me` 200, `POST /games` 201 |
| Image import works on Vertex | `POST /ai/image-to-puzzle` 200 in 23s — `modelName: gemini-3.8-flash`, `validPuzzle: true`, 9x9 grid |
| No AWS anywhere | `gcloud run services describe` on both: zero `AWS_*`/`BEDROCK_*`, zero secret-sourced vars |
| **D1 holds live** | image recognition has no `GCP_REGION`; the global endpoint default is what makes the import above succeed |
| G3 closed | RC frontend served from `sudoku-eo-2026-rcg-phase6.web.app`; `sudoku-eo-2026.web.app` still returns Site Not Found |
| Production untouched | `pulumi preview` on app prod: 15 unchanged, throughout |
| Teardown leaves nothing | `pulumi destroy` + `stack rm`: 14 deleted, then zero Cloud Run services, only the `(default)` Firestore database, only the `DEFAULT_SITE`, and only production's web app — the RC web app was deleted rather than abandoned, as `abandon_web_app=is_prod` intends |
| A from-scratch deploy works | Stack `rcg-p6v2` created from nothing: all five jobs green, health/auth/games 200/200/201, image import 200 on `gemini-3.8-flash`, no AWS variable on either service, frontend on its own site, `sudoku-eo-2026.web.app` still Site Not Found |

### What the first three runs cost, and why

Each failure was invisible to every local check, and each error message named something other
than the cause. They are recorded because the next clean-room rebuild meets all three.

1. **`requirements-vertex.txt` not copied into the Cloud Run image.** A Phase 5 gap: the provider
   split landed without updating `Dockerfile.cloudrun`, and nothing rebuilt that image until now.
   The AWS Lambda `Dockerfile` has the same omission and is **not** fixed here.
2. **`bootstrap` still on the passphrase secrets provider.** The app stack reads it by
   `StackReference`, which constructs the referenced stack's secrets manager — so CI needed a
   passphrase it must never hold. Re-keyed onto the KMS key `bootstrap` itself created. Invisible
   locally because a developer shell exports `PULUMI_CONFIG_PASSPHRASE`.
3. **Ephemeral stacks lost their secrets-provider declaration between runs.** A self-managed
   backend keeps it in `Pulumi.<stack>.yaml`, which ephemeral stacks do not commit, so run two
   fell back to the passphrase provider. Both workflows now write that file when absent — and
   only when absent, so a prod dispatch cannot overwrite production's committed configuration.

A fourth trap was caught before it reached CI: `pulumi preview` on a fresh `rcg-*` stack showed
the prod stack owns three **project-level singletons** — the Firebase enrolment, the Identity
Platform tenant and its Google IdP. A second stack declaring them collides. They are now
production-only, which means an RC stack's own `*.web.app` origin is not an authorised OAuth
origin; `localhost` is, on every stack, and that is the supported way to exercise a real Google
sign-in against an RC backend — the same method Phase 4's continuity check used.

## 10. Open item — Hosting site names are not reusable

`pulumi destroy` removes a Firebase Hosting site, but Firebase **tombstones its name**:

```
Error creating Site: Invalid name: `sudoku-eo-2026-rcg-phase6` is reserved by another
project; try something like `sudoku-eo-2026-rcg-phase6-52aba` instead
```

The message says *another project*; the name is in fact held by our own deleted site. So a stack
that has been torn down cannot be recreated under the same name, and an `rcg-*` branch recreated
after its teardown deploys everything up to the Hosting site and then fails — leaving a partial
environment that needs destroying by hand.

This is a property of Firebase Hosting, not of anything this phase introduced; it was simply
never reachable before, because no earlier phase exercised teardown followed by redeploy. It cost
the first clean-deploy attempt, which is why `rcg-p6v2` exists alongside `rcg-phase6`.

`hosting_site_id` is `{project_id}-{stack}` and the Firebase cap is 30 characters, so with
`sudoku-eo-2026` there are 15 left for the stack and none spare for a uniquifying suffix. Three
ways out, in the order they are worth considering:

| Option | Effect | Cost |
| --- | --- | --- |
| Drop `project_id` from the non-production site id — `sudoku-{stack}` | Frees 8 characters, enough for a suffix | Non-production site ids change; production's stays `sudoku-eo-2026` |
| Add a `random.RandomId` suffix, held in stack state | Stable for a stack's life, fresh on recreation — fixes the problem outright | Needs the freed budget above; adds a provider |
| Accept it and fail early with a legible message | No naming change | Redeploying a torn-down branch name stays impossible; the operator must pick a new one |

The first two compose: free the budget, then suffix. Deferred rather than decided, because it
changes `naming.py` — the shared source of truth for CI and both programs — and the `rcg-*`
branch-naming convention with it.
