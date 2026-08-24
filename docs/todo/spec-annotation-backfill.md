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
  same file. **Done** (2026-08-24) for 38 of 39 — see the note below on `FE-UI-042b`.
  `FE-UI-030`/`FE-UI-050` (the "Import from Image" menu entry and the dev hint-demo submenu) were
  found to live in `Header.jsx`, not one of the originally-listed files, and were annotated there.
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

**`FE-UI-042b` gap (found 2026-08-24, during the frontend pass):** "compute a provisional per-game
score for won games based on difficulty base score, elapsed time, and hints used" has no frontend
implementation at all — `HistoryView.jsx` only reads `entry.score` (already server-computed), and
the actual scoring logic (`ScoringConstants.java`, `GameServiceImpl.java`) is backend/Java, not
`ui/src/`. The spec's own text ends with "(TODO: replace with server-side scoring system)" —
scoring appears to have already moved server-side since this ID was written, making `FE-UI-042b`
itself possibly stale (the ID may need re-scoping to a `GL-BE-*`/backend ID, not annotating as
frontend at all). **Left uncited, out of scope for this frontend annotation pass** — flagging as a
spec-drift question, not just a missing citation.

**Current state:** `docs/arrows/index.yaml`'s `cloud-platform` entry carries a `drift` note pointing
here as of 2026-08-19 (its AWS and GCP Terraform halves were done 2026-08-24 on separate branches —
`docs/spec-annotation-aws-terraform`, `docs/spec-annotation-gcp-terraform` — not yet merged as of
this branch's base; either way the note stays open for the CI/bootstrap-script gap). The
`sudoku-coach` and `image-recognition` entries' drift notes were cleared 2026-08-21 and 2026-08-24
respectively. `react-frontend`'s drift note is cleared on this branch, with the caveat that
`FE-UI-042b` is a distinct, unresolved gap (see above).

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

- [ ] `infra/aws/*.tf` has `@spec` annotations covering 25 of 26 `CP-INFRA-*` IDs (done on branch `docs/spec-annotation-aws-terraform`, not yet merged as of this branch's base)
- [ ] `infra/gcp/*.tf` has `@spec` annotations covering 21 of 30 `CP-GCP-*` IDs (done on branch `docs/spec-annotation-gcp-terraform`, not yet merged as of this branch's base)
- [x] The listed frontend files have `@spec` annotations covering 38 of 39 `FE-UI-*`/`FE-BE-*`/`FE-MOB-*` IDs — done 2026-08-24; `FE-UI-042b` scoped out, see the note above
- [x] `image_recognition/handler.py` and `infra/gcp/image_recognition.tf` have `@spec` annotations covering all 20 `IR-*` IDs — done 2026-08-21
- [x] `sudoku-coach`'s 5 uncited IDs (`SC-BE-004`, `SC-UI-041`, `SC-UI-060/061/062`) annotated — done 2026-08-21
- [x] `docs/arrows/index.yaml`'s `drift` notes cleared where fully resolvable: `sudoku-coach` (2026-08-21), `image-recognition` (2026-08-24), `react-frontend` (2026-08-24, with the `FE-UI-042b` caveat). `cloud-platform`'s stays open — its Terraform work is done (pending merge) but the CI/bootstrap-script gap is new, distinct scope.

## Related specs / docs

- [`docs/specs/cloud-platform-specs.md`](../specs/cloud-platform-specs.md) — `CP-INFRA-*`, `CP-GCP-*`
- [`docs/specs/react-frontend-specs.md`](../specs/react-frontend-specs.md) — `FE-UI-*`, `FE-BE-*`, `FE-MOB-*`
- [`docs/specs/image-recognition-specs.md`](../specs/image-recognition-specs.md) — `IR-*`
- [`docs/arrows/index.yaml`](../arrows/index.yaml) — the three affected entries' `drift` fields
