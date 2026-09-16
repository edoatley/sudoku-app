# GCP Pulumi Re-Platform — Handoff

**Updated**: 2026-09-16
**Status**: Phases 0–5 complete. Phase 6 is next and unblocked.
**Umbrella plan**: `docs/planning/gcp-pulumi-replatform.md`

Read this first on a cold start. It states what exists, what is verified, and the traps that cost
time — so the next session does not rediscover them.

---

## 1. What exists right now

Two live GCP projects. **`sudoku-app-eo` is the old one and still serves production** — it is
untouched and must stay that way until Phase 8.

**`sudoku-eo-2026`** is the new clean-room project, 100% Pulumi-built:

| Stack | Resources | Run by |
| --- | --- | --- |
| `sudoku-gcp-bootstrap/prod` | 53 — project, 16 APIs, GCS state, KMS, Artifact Registry, 3 service accounts, IAM, WIF, budget | a human, locally |
| `sudoku-gcp-app/prod` | 16 — Firestore, Firebase project + Hosting site + **Web App**, Cloud DNS zone, Identity Platform | CI (or a human) |

Both are idempotent: `pulumi up` and `pulumi refresh` each report no changes.

```bash
cd infra/gcp/bootstrap && pulumi stack output     # 10 outputs
cd infra/gcp/app       && pulumi stack output     # 13 outputs
make gcp-inventory                                 # resources + IAM drift
```

## 2. Verified facts — do not re-derive these

- **Identity continuity holds.** A Google sign-in on the new tenant returns
  `google.com` sub `112098963340592508392`, identical to the `userId` in the old project's
  Firestore. Existing users keep their data. The Firebase UID differs and that is fine — nothing
  keys on it.
- **Token shape matches the backend.** `iss=https://securetoken.google.com/sudoku-eo-2026`,
  `aud=sudoku-eo-2026`, `sign_in_provider=google.com` — what `%gcp` expects.
- **The privilege split is real.** A federated CI job can read the state bucket and Cloud Run, and
  is refused billing, `roles/owner`, WIF admin and Secret Manager.
- **Vertex passes the image-recognition accuracy gate** on `gemini-3.8-flash` — 100%, 5/5 exact,
  matching Bedrock. `IMAGE_AI_PROVIDER=vertex` is viable.
- **DNS is delegated.** `gcp.edoatley.co.uk` NS → Cloud DNS, verified with `dig +trace`.

## 3. Traps that have already cost time

| Trap | What happens |
| --- | --- |
| **Two quotas, not one** | Project *creation* and billing-account *linkage* are separate limits. Linkage caps at 5 and fails mid-apply. Check `gcloud billing projects list --billing-account 010F10-A51056-E8EC40 \| wc -l`. |
| **Quota-project APIs** | `billingbudgets` and `identitytoolkit` 403 under human ADC with a misleading `SERVICE_DISABLED`. Fix with a **scoped** `gcp.Provider` (`user_project_override`) — never stack-wide, which breaks `gcp.projects.Service`. |
| **`depends_on` doesn't reach children** | Components must merge caller opts. Enforced by `test_child_opts_propagation`. |
| **Missing API enablement** | Fails mid-apply, never at plan time. Enforced by `test_every_resource_kind_has_its_api_enabled`. |
| **Gemini 3.x is `global`-only** | A regional endpoint 404s with a message blaming the model name. |
| **Shared CI secrets** | `SMOKE_TEST_USER_PASSWORD` serves AWS Cognito *and* the old GCP project. Never overwrite it — use the `_NEXT` convention. |
| **Unverified string replaces** | Several edits silently matched nothing this session. Assert on every replacement. |

## 4. Phase 6 — compute and frontend (next)

**Goal:** a full end-to-end environment on the new project, proven on an ephemeral `rcg-*` stack
before prod.

- [ ] **6a.** Wire `ContainerService` twice in `app/__main__.py` — backend and image recognition.
      Env from stack outputs, not hand-typed: `GCP_PROJECT_ID`, `QUARKUS_PROFILE=gcp`,
      `QUARKUS_CONFIG_PROFILE_PARENT=prod`, `CORS_ALLOWED_ORIGINS`, `COACH_AI_PROVIDER=vertex`,
      **`IMAGE_AI_PROVIDER=vertex`**, `GCP_REGION`. **No AWS variables** — assert their absence.
- [ ] **6b.** CORS via `Output.all(...)` over the Hosting origins — the dependency that needs a
      post-apply script on AWS resolves inside the program here.
- [ ] **6c.** Build both images to Artifact Registry in the new project. `GCP_PROJECT_ID` can now
      be a repo **variable**, not a secret, which removes the tag-only-crosses-jobs workaround.
- [ ] **6d.** Frontend build + `firebase deploy`, using `firebase_api_key`, `firebase_auth_domain`
      and `hosting_site_id` from the stack, plus `VITE_AUTH_PROVIDER=firebase`.
- [ ] **6e.** Ephemeral `rcg-*` stack: create, deploy, smoke, then `pulumi destroy` + `stack rm`
      leaving zero residue. **Never `--force`.**
- [ ] **6f.** Target the RC Hosting site explicitly (`firebase target:apply`) — closes gap G3,
      where RC frontends had a site but no deploy target.
- [ ] **6g.** Delete `scripts/github/gcp-workspace-name.sh`; `components/naming.py` is authoritative.

**Done when:** both services healthy, `/api/v1/q/health/ready` 200, a real sign-in works on the RC
URL, an image import succeeds with **no AWS credentials anywhere** in the Cloud Run env, teardown
is clean, and prod is untouched.

Flip `CP-GCP-001..004`, `-010..013`, `-040..043`, `CP-PUL-020`, `-023`.

## 5. Phases 7–9 (outlines; write the detailed plan just ahead of each)

**Phase 7 — unified deploy workflow.** `ci-deploy.yml` becomes the single entry point with a
`select-target` job: dispatch input wins; `rc-*`→AWS and `rcg-*`→GCP by branch; `main` reads
`vars.DEPLOY_TARGET` (default `aws`). `deploy-gcp.yml` becomes `workflow_call`-only and loses its
eight phased inputs. Regression bar: with `DEPLOY_TARGET=aws`, a push to `main` must behave
exactly as today. Specs `CP-CD-001..004`, `CP-PUL-080`.

**Phase 8 — production cutover.** Set `enableCustomDomain`, wait for the Google-managed cert
(minutes to ~24h), smoke, rename the `_NEXT` secrets, set `DEPLOY_TARGET=gcp`, remove the old
Route53 CNAME. **Firestore starts empty** — the GCP leaderboard and game history reset; say so in
the release note.

**Phase 8b — Cognito OAuth client** (parallel, can start now). The current Google OAuth client
lives in the old project and is shared with AWS Cognito, so **the old project cannot be deleted
until Cognito has its own**. Backlog row 4.

**Phase 9 — decommission.** Delete `infra/gcp/*.tf`, six `scripts/infra/gcp/*.sh` (including
`identity-platform-bootstrap.sh`, deferred from 4h), `scripts/github/gcp-workspace-name.sh`; drop
the AWS IAM user `sudoku-bedrock-cross-cloud`; strike `CP-GCP-085` and `IR-GCP-005`. Then the
dated backlog row to delete `sudoku-app-eo`, **blocked on 8b**.

## 6. Where things live

| | |
| --- | --- |
| Umbrella plan | `docs/planning/gcp-pulumi-replatform.md` |
| Per-phase plans | `docs/planning/gcp-pulumi-phase-{1,2,3,4,5}-*.md` |
| Design | `docs/llds/cloud-platform-gcp.md` |
| Specs | `docs/specs/cloud-platform-specs.md` (`CP-GCP-*`, `CP-PUL-*`, `CP-CD-*`) |
| Runbook | `docs/runbooks/gcp-manual-setup.md` |
| Open work | `docs/backlog.md`, `docs/todo/` |

**Still manual, permanently:** the OAuth consent screen and client (no API exists), and the
Identity Platform smoke user (no IaC resource). Everything else is code.
