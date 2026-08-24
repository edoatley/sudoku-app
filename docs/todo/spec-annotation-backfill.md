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
- **`infra/aws/*.tf` (26 IDs, all `CP-INFRA-*` — the doc originally said 25; the spec file has 26)**
  — confirmed **zero** `@spec` annotations anywhere in `infra/aws/` (`amplify.tf`,
  `api_gateway.tf`, `budgets.tf`, `cognito.tf`, `cognito-rc-shared.tf`, `domain.tf`, `dynamodb.tf`,
  `iam.tf`, `image_recognition_lambda.tf`, `lambda.tf`, `main.tf`, `migrations.tf`, `outputs.tf`,
  `terraform.tf`, `variables.tf`). **Done** (2026-08-24) for 25 of 26 — see the note below on
  `CP-INFRA-061`. The
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

- **`sudoku-coach` (5 IDs: `SC-BE-004`, `SC-UI-041`, `SC-UI-060/061/062`)** — smaller, separately
  flagged gap on the `sudoku-coach` arrow entry rather than this doc's original scoping (found
  2026-08-19, not folded in until 2026-08-21). `SC-BE-004` cites cleanly at
  `BoardFormatter.format()`. The other four are emergent from shared state / a `key`-based remount
  pattern rather than single function bodies — `SC-UI-061`/`062` sit on the `CoachWidget`'s `key`
  prop in `App.jsx`, `SC-UI-041`/`060` on `useCoachSession.js`'s file header and its
  `setHighlightCells` call site. **Done** (2026-08-21) — see the `sudoku-coach` row below.

**`CP-INFRA-061` gap (found 2026-08-24, during the AWS Terraform pass):** "share the Lambda zip S3
bucket across all workspaces" has no home in `infra/aws/*.tf` — no `.tf` resource, data source, or
variable references the `sudoku-lambda-zip-{account}` bucket at all. The only reference found is an
IAM policy grant (`LambdaZipBucket` statement, including `s3:CreateBucket`) in
`scripts/infra/aws/bootstrap.sh`, which suggests the bucket may be created out-of-band or as an
implicit side effect of the `terraform-aws-modules/lambda` module rather than by an explicit
resource — genuinely unclear without further investigation, and this project's other
CI/bootstrap scripts do carry `@spec` citations (e.g. `scripts/github/amplify-remove-rc-urls.sh`),
so a shell-script citation wouldn't be unprecedented if that turns out to be the right home.
**Left unannotated, scoped out of the AWS Terraform PR** rather than force a citation onto an
ambiguous location — needs its own short investigation to confirm where the bucket is actually
provisioned before annotating.

**Current state:** `docs/arrows/index.yaml`'s `cloud-platform` and `react-frontend` entries carry a
`drift` note pointing here as of 2026-08-19 (cloud-platform's GCP half — 23 `CP-GCP-*` IDs — is
still open). The `sudoku-coach` and `image-recognition` entries' drift notes were cleared
2026-08-21 and 2026-08-24 respectively.

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

- [x] `infra/aws/*.tf` has `@spec` annotations covering 25 of 26 `CP-INFRA-*` IDs — done 2026-08-24;
      `CP-INFRA-061` scoped out, see the note above
- [ ] `infra/gcp/*.tf` has `@spec` annotations covering all 23 `CP-GCP-*` IDs listed above
- [ ] The listed frontend files have `@spec` annotations covering all 39 `FE-UI-*`/`FE-BE-*`/`FE-MOB-*` IDs
- [x] `image_recognition/handler.py` and `infra/gcp/image_recognition.tf` have `@spec` annotations covering all 20 `IR-*` IDs — done 2026-08-21
- [x] `sudoku-coach`'s 5 uncited IDs (`SC-BE-004`, `SC-UI-041`, `SC-UI-060/061/062`) annotated — done 2026-08-21
- [ ] `docs/arrows/index.yaml`'s `drift` notes on `cloud-platform`, `react-frontend`, `image-recognition` cleared (`sudoku-coach`'s cleared 2026-08-21; `cloud-platform`'s AWS half done here but its GCP half keeps the note open)

## Related specs / docs

- [`docs/specs/cloud-platform-specs.md`](../specs/cloud-platform-specs.md) — `CP-INFRA-*`, `CP-GCP-*`
- [`docs/specs/react-frontend-specs.md`](../specs/react-frontend-specs.md) — `FE-UI-*`, `FE-BE-*`, `FE-MOB-*`
- [`docs/specs/image-recognition-specs.md`](../specs/image-recognition-specs.md) — `IR-*`
- [`docs/arrows/index.yaml`](../arrows/index.yaml) — the three affected entries' `drift` fields
