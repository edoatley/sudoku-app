# Implementation Plan: GCP Pulumi — Phase 1, Scaffolding & Validation Gate

**Status**: Approved — ready to implement
**Created**: 2026-09-11
**Origin**: `docs/planning/gcp-pulumi-replatform.md` §4
**Arrow**: `cloud-platform` (`docs/arrows/cloud-platform.md`) — GCP facet

---

## 1. Goal

The whole Pulumi component library exists, is unit-tested against Pulumi's mock runtime, and is
gated in CI and the pre-push suite. **Nothing is deployed and no GCP project is created.**

This phase exists so that Phase 2's first contact with a real cloud runs code that has already
been exercised. It also delivers the first infrastructure unit tests in this repository — the
AWS facet's "no Terratest or equivalent" gap has stood because HCL offers no cheap seam;
`pulumi.runtime.set_mocks()` does.

## 2. Prerequisite

Install the Pulumi CLI (`brew install pulumi`). `uv 0.12.7` and `gcloud 583` are already present.
Confirm `runtime.options.toolchain: uv` is supported by the installed CLI — if not, fall back to
an explicit `uv venv && uv sync` step and record the deviation in the LLD.

## 3. Files created

```
infra/gcp/
├── pyproject.toml            # project "sudoku-gcp-infra"; deps: pulumi, pulumi-gcp
├── uv.lock                   # COMMITTED — fixes the unpinned-provider problem the .tf lock files have
├── .gitignore                # .venv/ __pycache__/ .pytest_cache/  (NOT uv.lock)
├── components/
│   ├── __init__.py
│   ├── naming.py             # stack -> suffix, label sanitisation, site_id length rule
│   ├── project_foundation.py # ProjectFoundation
│   ├── artifact_registry.py  # ArtifactRegistry
│   ├── service_identity.py   # ServiceIdentity
│   ├── workload_identity.py  # WorkloadIdentityFederation
│   ├── cost_guardrails.py    # CostGuardrails
│   ├── firestore_database.py # FirestoreDatabase
│   ├── container_service.py  # ContainerService
│   ├── identity_platform.py  # IdentityPlatform
│   ├── static_site.py        # StaticSite
│   └── dns_zone.py           # DnsZone
├── bootstrap/{Pulumi.yaml,__main__.py}   # written, never run
├── app/{Pulumi.yaml,__main__.py}         # written, never run
└── tests/
    ├── conftest.py                 # pulumi.runtime.set_mocks
    ├── test_naming.py
    ├── test_container_service.py
    ├── test_firestore_database.py
    ├── test_cost_guardrails.py
    └── test_no_authoritative_iam.py
```

Pulumi files co-locate in `infra/gcp/` alongside the existing `*.tf`. They do not collide,
`terraform fmt`/`validate` ignores non-`.tf` files, and `checkov -d infra/gcp/` is unaffected —
so Terraform and Pulumi coexist through Phase 8 and Phase 9 becomes a pure deletion.

## 4. Files modified

- `.github/workflows/ci.yml` — new `ci-pulumi` job, triggered on `infra/gcp/**`
- `scripts/local/local-alltests.sh` — new Suite 8
- `Makefile` — `ruff` over `infra/gcp`
- `infra/gcp/README.md` — Pulumi section added (Terraform section stays until Phase 9)

## 5. Work items

- [ ] **1a. Create the uv project.** `infra/gcp/pyproject.toml` declaring `sudoku_gcp_infra` as
  an installable package exporting `components`, so imports read
  `from components.container_service import ContainerService` with no `sys.path` manipulation and
  `uv run pytest` works from any directory. Commit `uv.lock`.
  @spec CP-PUL-070

- [ ] **1b. `components/naming.py`.** Port the stack-name derivation from
  `scripts/github/gcp-workspace-name.sh` to Python: lowercase, `/.` → `-`, strip to `[a-z0-9-]`,
  cap at `30 - len(project_id) - 1` (the Firebase `site_id` limit, a Firebase constraint that
  survives the Terraform→Pulumi move), strip trailing hyphen. Also `suffix()`,
  `environment_label()`, and `firestore_database_id()`.
  **Do not port `is_rc`** — `infra/gcp/main.tf:3` defines it as `startswith(workspace, "rc-")`,
  which never matches because GCP workspaces derive from `rcg-*` branches. That dead local is a
  known drift item (see Phase 0); the Pulumi port must not reproduce it.
  @spec CP-PUL-021

- [ ] **1c. `ProjectFoundation`.** Wraps `gcp.organizations.Project`
  (`auto_create_network=False` — suppresses the default VPC and its four firewall rules, which
  the old project has and does not want), `gcp.billing.ProjectInfo`, `gcp.projects.Service` × N,
  `gcp.storage.Bucket` (versioning, `uniform_bucket_level_access=True`,
  `public_access_prevention="enforced"`), `gcp.kms.KeyRing`, `gcp.kms.CryptoKey`.
  Every `gcp.projects.Service` gets `disable_on_destroy=False`, otherwise destroying the
  bootstrap stack would start disabling APIs under a live app stack.
  Outputs `project_id`, `project_number`, `state_bucket_url`, and
  `kms_key_uri = Output.concat("gcp-kms://", key.id)` — the secrets-provider URI assembled from a
  value that does not exist until apply.
  @spec CP-PUL-001, CP-PUL-002, CP-PUL-040

- [ ] **1d. `ArtifactRegistry`.** One `gcp.artifactregistry.Repository` with the two cleanup
  policies carried over from `bootstrap.sh` (`keep-10-recent`: keep 10 most recent versions;
  `delete-older-untagged`: untagged and older than `2592000s`).
  **One repository, not two** — `bootstrap.sh` creates `sudoku-image-recognition` but CI has
  never used it; both images go to `sudoku-backend` under different image names. Dropping it
  closes documented waste.
  @spec CP-PUL-003

- [ ] **1e. `ServiceIdentity`.** `gcp.serviceaccount.Account` plus one `gcp.projects.IAMMember`
  per role. Outputs `email` and `member = Output.concat("serviceAccount:", sa.email)`.
  **Use `IAMMember` (additive) only — never `IAMBinding` (authoritative per role) or `IAMPolicy`
  (authoritative for the whole project).** An `IAMPolicy` would silently delete every other
  binding on the project, including the operator's own Owner grant. This is the single largest
  footgun in the migration.
  @spec CP-PUL-010, CP-PUL-013

- [ ] **1f. `WorkloadIdentityFederation`.** `gcp.iam.WorkloadIdentityPool`,
  `gcp.iam.WorkloadIdentityPoolProvider` (issuer `https://token.actions.githubusercontent.com`,
  mapping `google.subject=assertion.sub,attribute.repository=assertion.repository`, condition
  `assertion.repository == 'edoatley/sudoku-app'`), and `gcp.serviceaccount.IAMMember`
  (`roles/iam.workloadIdentityUser`). The impersonation member is built from the pool's
  server-assigned name:
  ```python
  principal_set = pulumi.Output.concat(
      "principalSet://iam.googleapis.com/", pool.name,
      "/attribute.repository/", github_repo,
  )
  ```
  This is the canonical "cannot be known until the resource exists" case — `github-bootstrap.sh`
  has to shell out to `gcloud ... --format='value(name)'` to discover it.
  @spec CP-PUL-011, CP-PUL-012

- [ ] **1g. `FirestoreDatabase`.** `gcp.firestore.Database`, `gcp.firestore.Field` (TTL),
  `gcp.firestore.Index` × N. Takes a frozen `IndexSpec` dataclass rather than dicts, so it is
  typed and unit-testable. Carries over verbatim: PITR + delete protection on prod only, the
  `coachRateLimits.expiresAt` TTL, and the `games(userId ASC, status ASC, endedAt DESC)`
  composite index. Prod gets `ResourceOptions(protect=True, retain_on_delete=True)` — strictly
  stronger than Terraform's `deletion_policy=ABANDON`, since `protect` also blocks replacement.
  @spec CP-PUL-022

- [ ] **1h. `ContainerService` — the flagship component.** `gcp.cloudrunv2.Service` plus an
  optional `gcp.cloudrunv2.ServiceIamMember` for the `allUsers` invoker. Instantiated twice
  (backend, image recognition) from one class, replacing `cloud_run.tf` (158 lines) and
  `image_recognition.tf` (129 lines) which are ~70% identical.
  `public=True` for **every** stack including prod, closing gap G1 and deleting
  `scripts/infra/gcp/grant-prod-invoker.sh`: Terraform kept prod's invoker manual so a plan could
  not toggle prod reachability, but under the two-stack split the app stack already holds
  `run.admin` and can delete the service outright, so withholding one additive binding buys
  nothing and costs a manual step on every recreation.
  @spec CP-PUL-020, CP-PUL-023

- [ ] **1i. `IdentityPlatform`, `StaticSite`, `DnsZone`, `CostGuardrails`.** Per the component
  table in `docs/llds/cloud-platform-gcp.md`. `StaticSite` needs no `google-beta` provider —
  `pulumi-gcp` merges GA and beta into one SDK, so the `provider = google-beta` lines in
  `firebase_hosting.tf` simply vanish.
  @spec CP-PUL-030, CP-PUL-050, CP-PUL-060

- [ ] **1j. Skeleton `bootstrap/__main__.py` and `app/__main__.py`.** Written so `pulumi preview`
  type-checks the wiring, but not run against any cloud in this phase.

- [ ] **1k. Unit tests.** `conftest.py` installs `pulumi.runtime.set_mocks(...)`; each test
  instantiates a component and asserts on its arguments — e.g. Cloud Run gets
  `min_instance_count=0`; the invoker member is `allUsers` only when `public=True`; prod gets
  `deletion_protection=True`; the naming rule caps at the Firebase limit and strips trailing
  hyphens.
  @spec CP-PUL-081

- [ ] **1l. `test_no_authoritative_iam.py`.** Scans `components/*.py` and fails if
  `IAMPolicy` or `IAMBinding` appears. A cheap structural guardrail against 1e's footgun.
  @spec CP-PUL-013

- [ ] **1m. CI gate.** `ci-pulumi` job in `.github/workflows/ci.yml`: `uv sync --frozen`,
  `uv run ruff check`, `uv run pytest infra/gcp/tests`. Path-filtered to `infra/gcp/**`. No cloud
  credentials — this job must never need them.
  @spec CP-PUL-081

- [ ] **1n. Pre-push suite.** New Suite 8 in `scripts/local/local-alltests.sh` with a
  `--skip-pulumi` flag, following the existing suite structure. This also partially closes
  backlog item 4 (`docs/todo/terraform-ci-testing-review.md`), whose GCP half asked for exactly
  this coverage.
  @spec CP-PUL-082

## 6. Definition of Done

- [ ] `cd infra/gcp && uv sync --frozen && uv run pytest` — green, ≥80% coverage of `components/`
- [ ] `uv run ruff check infra/gcp` — clean
- [ ] `test_no_authoritative_iam.py` fails when an `IAMPolicy` is deliberately introduced, and
      passes when it is removed (verify the guardrail actually guards)
- [ ] `ci-pulumi` runs on a PR touching `infra/gcp/**` and passes
- [ ] `bash scripts/local/local-alltests.sh` lists the new suite in its summary table
- [ ] `bash scripts/local/local-alltests.sh` passes in full
- [ ] **No GCP resource exists.** `gcloud projects list` is unchanged.
- [ ] `CP-PUL-070`, `-081`, `-082`, `-013` flipped to `[x]`; the remaining `CP-PUL-*` stay `[ ]`
- [ ] `docs/arrows/cloud-platform.md` EARS coverage table updated
- [ ] `docs/arrows/testing-strategy.md` records the new Pulumi component-test layer

## 7. Out of scope

- Creating any cloud resource (Phase 2)
- Deleting any `.tf` file or bash script (Phase 9)
- Touching `infra/aws/`
