# Backfill `@spec` annotations for infra, plain frontend components, and image_recognition

**Summary:** ~107 implemented (`[x]`) EARS specs have zero `@spec` citation anywhere in the
codebase — not because the behavior is unimplemented, but because whole subsystems (AWS/GCP
Terraform, several frontend hooks/components, `image_recognition/handler.py`) were never
annotated at all.

**Branch context:** `main` — surfaced during the first `/arrow-maintenance` coverage audit pass
after installing the plugin (session 2026-08-19).

## Why deferred

Five parallel agents verified each ID against the actual implementing code this session and
either extended an existing `@spec` citation (36 IDs resolved that way) or confirmed the
behavior is genuinely implemented but the file has **no** existing citation to extend — adding
~107 fresh annotations from scratch is much higher-judgment work than extending a sibling
citation, and out of scope for a same-session audit-and-fix pass. Filed as its own follow-up.

## Context

**Relevant files (by cluster, from the agent reports):**
- **`infra/aws/*.tf` (25 IDs, all `CP-INFRA-*`)** — confirmed **zero** `@spec` annotations
  anywhere in `infra/aws/` (`amplify.tf`, `api_gateway.tf`, `budgets.tf`, `cognito.tf`,
  `cognito-rc-shared.tf`, `domain.tf`, `dynamodb.tf`, `iam.tf`, `image_recognition_lambda.tf`,
  `lambda.tf`, `main.tf`, `migrations.tf`, `outputs.tf`, `terraform.tf`, `variables.tf`). The
  convention isn't used in this facet's Terraform at all.
- **`infra/gcp/*.tf` (23 IDs, all `CP-GCP-*`)** — primary resource blocks
  (`google_cloud_run_v2_service.backend`/`.image_recognition`, `google_firestore_database.main`,
  `google_firebase_hosting_site.frontend`, `google_firebase_hosting_custom_domain.frontend`,
  `google_billing_budget.monthly`, label locals in `main.tf`) carry no citation. The few
  citations that exist (`CP-GCP-014`, `CP-GCP-090`/`SC-GCP-007`, `GL-GCP-007`) are on narrower,
  unrelated sub-blocks.
- **`ui/src/hooks/{useSudokuGame,useGameSync,useGameTimer}.js`, `ui/src/api/sudokuApi.js`,
  `ui/src/App.jsx`, `ui/src/main.jsx`, `ui/src/components/{NumberPad,SudokuGrid,SudokuCell,
  HintDialog,ImportModal,AvatarPickerDialog,DevDataDialog}.jsx`, `ui/src/hooks/
  usePlayerProfile.js`, `ui/src/components/views/StatisticsView.jsx` (39 IDs: `FE-UI-*`,
  `FE-BE-*`, `FE-MOB-*`)** — each verified genuinely implemented; zero `@spec` comments in most
  of these files, or an existing citation that covers a different, unrelated ID range in the
  same file.
- **`image_recognition/handler.py` (19 IDs: `IR-API-*`, `IR-BE-*`, `IR-PROC-*`)** — verified
  line-by-line (warmup route, image validation, Bedrock Converse call, JSON/pipe-table parsing,
  scoring). Only `IR-PROC-013` is cited anywhere, and only in a test file, never in `handler.py`
  itself.
- **`infra/gcp/image_recognition.tf` (`IR-GCP-005`)** — the `dynamic "env"` blocks wiring
  Bedrock creds from Secret Manager; verified implemented, uncited.

**Current state:** `docs/arrows/index.yaml`'s `cloud-platform`, `react-frontend`, and
`image-recognition` entries carry a `drift` note pointing here as of 2026-08-19.

**Key constraints:**
- Per this project's `@spec` convention, annotations go "at the entry point of the behavior's
  implementation graph, not every helper" — a single annotation per file/resource block citing
  the full range it covers is the right shape, not one per line.
- Terraform annotation style: `# @spec ID1, ID2` above the resource block (see `CP-GCP-014` in
  `infra/gcp/cloud_run.tf` for the existing convention).
- This is documentation/traceability work — no behavior changes. Low risk, but high volume
  (~107 IDs across maybe 25-30 files).

## What to do

1. Work through one cluster at a time (AWS Terraform, GCP Terraform, frontend, image
   recognition) rather than all at once — each is independently reviewable.
2. For each file, read its EARS Coverage table row in the owning arrow doc
   (`docs/arrows/cloud-platform.md`, `docs/arrows/react-frontend.md`,
   `docs/arrows/image-recognition.md`) to get the exact ID range per category, then add one
   `@spec` annotation per resource block/component/function citing the IDs it implements.
3. Re-run the coverage check (`grep -rhoE '@spec [A-Za-z0-9,\. -]+' ...` per the audit-checklist
   methodology, or `/arrow-maintenance` once available) to confirm the count drops to ~0 for
   these clusters.
4. Clear the `drift` notes on the affected `index.yaml` entries once done.

## Acceptance criteria

- [ ] `infra/aws/*.tf` has `@spec` annotations covering all 25 `CP-INFRA-*` IDs listed above
- [ ] `infra/gcp/*.tf` has `@spec` annotations covering all 23 `CP-GCP-*` IDs listed above
- [ ] The listed frontend files have `@spec` annotations covering all 39 `FE-UI-*`/`FE-BE-*`/`FE-MOB-*` IDs
- [x] `image_recognition/handler.py` and `infra/gcp/image_recognition.tf` have `@spec` annotations covering all 20 `IR-*` IDs — done 2026-08-21
- [ ] `docs/arrows/index.yaml`'s `drift` notes on `cloud-platform`, `react-frontend` cleared (`image-recognition` cleared 2026-08-21)

## Related specs / docs

- [`docs/specs/cloud-platform-specs.md`](../specs/cloud-platform-specs.md) — `CP-INFRA-*`, `CP-GCP-*`
- [`docs/specs/react-frontend-specs.md`](../specs/react-frontend-specs.md) — `FE-UI-*`, `FE-BE-*`, `FE-MOB-*`
- [`docs/specs/image-recognition-specs.md`](../specs/image-recognition-specs.md) — `IR-*`
- [`docs/arrows/index.yaml`](../arrows/index.yaml) — the three affected entries' `drift` fields
