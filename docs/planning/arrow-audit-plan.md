# Arrow Audit Plan — MAPPED → AUDITED

**Created:** 2026-04-18  
**Goal:** Move all 7 MAPPED arrows to AUDITED status.  
**Approach:** One commit per arrow. Each arrow requires: test audit + must/should-fix code changes + arrow doc updated to AUDITED.

## Definition of AUDITED

An arrow reaches AUDITED when:
1. All test files covering that arrow have been read and verified to trace to current EARS specs
2. All **Must Fix** items are resolved
3. All **Should Fix** items are resolved (or explicitly deferred with justification)
4. The arrow doc status field is updated to `AUDITED` with the date
5. `docs/arrows/index.yaml` status field is updated to `AUDITED`

Nice-to-have items may remain as notes — they do not block AUDITED.

## Arrow Order

Ordered by dependency (lower layers first):

1. `sudoku-logic` — domain primitives, no dependencies
2. `hint-engine` — depends on sudoku-logic
3. `puzzle-generation` — depends on hint-engine
4. `game-lifecycle` — depends on puzzle-generation
5. `user-management` — independent of game logic
6. `cloud-platform` — infrastructure, no code tests
7. `react-frontend` — depends on all backend arrows

---

## Arrow 1: sudoku-logic

**Status:** [x] Complete

### Test files to audit
- `backend/src/test/java/com/sudoku/domain/BoardTest.java`
- `backend/src/test/java/com/sudoku/domain/CellTest.java`

### Work items
- [ ] Audit `BoardTest.java` — verify tests cover SL-DATA-001 to 005, SL-PROC-001 to 003, SL-PROC-004 to 006; add `@spec` annotations
- [ ] Audit `CellTest.java` — verify tests cover SL-DATA-006 to 008; add `@spec` annotations
- [ ] **Should Fix:** `Board.fromGrid()` currently throws `IllegalArgumentException`; introduce `InvalidGridException` (or reuse `InvalidPuzzleException`) and throw it from `fromGrid()` directly, eliminating the translation step in callers (SL-DATA-003, SL-DATA-004)
- [ ] Update arrow doc: status → AUDITED, tests section → audited
- [ ] Update `index.yaml`: status → AUDITED

### Commit message
`audit: sudoku-logic arrow — test annotations, InvalidGridException`

---

## Arrow 2: hint-engine

**Status:** [x] Complete

### Test files to audit
- `backend/src/test/java/com/sudoku/puzzle/SudokuServiceImplTest.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/BoardUtilsTest.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/FullHouseStrategyTest.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/HiddenPairStrategyTest.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/HiddenSingleStrategyTest.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/HiddenTripleStrategyTest.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/NakedPairStrategyTest.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/NakedSingleStrategyTest.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/NakedTripleStrategyTest.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/PointingPairStrategyTest.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/SwordfishStrategyTest.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/XWingStrategyTest.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/YWingStrategyTest.java`

### Work items
- [ ] Audit all strategy tests — verify each maps to its HE-BE-010–020 spec; add `@spec` annotations
- [ ] Audit `SudokuServiceImplTest.java` — verify coverage of HE-BE-001 to 007, HE-BE-030 to 035, HE-API-001 to 006; add `@spec` annotations
- [ ] Audit `BoardUtilsTest.java` — verify geometry helper coverage
- [ ] **Should Fix:** Add comment in `NakedPairStrategy.java` documenting the rank=30 vs MEDIUM label discrepancy (HE-BE-012)
- [ ] Update arrow doc: status → AUDITED, tests section → audited
- [ ] Update `index.yaml`: status → AUDITED

### Commit message
`audit: hint-engine arrow — test annotations, NakedPair rank comment`

---

## Arrow 3: puzzle-generation

**Status:** [x] Complete

### Test files to audit
- `backend/src/test/java/com/sudoku/puzzle/PuzzleGeneratorTest.java`
- `backend/src/test/java/com/sudoku/puzzle/PuzzleResourceTest.java`
- `backend/src/test/java/com/sudoku/puzzle/developer/MockSudokuServiceTest.java`
- `backend/src/test/java/com/sudoku/puzzle/developer/HintDemoGridsTest.java`
- `backend/src/test/java/com/sudoku/puzzle/developer/DevResourceTest.java`
- `backend/src/test/java/com/sudoku/puzzle/developer/PuzzleCandidateFinderTest.java`

### Work items
- [ ] Audit `PuzzleGeneratorTest.java` — verify PG-BE-001 to 006; add `@spec` annotations
- [ ] Audit `PuzzleResourceTest.java` — verify PG-API-001 to 004; add `@spec` annotations
- [ ] Audit `MockSudokuServiceTest.java`, `HintDemoGridsTest.java`, `DevResourceTest.java` — verify PG-DEV-001 to 004; add `@spec` annotations
- [ ] Fix duplicate entry in arrow doc Work Required section (items 1 and 2 are identical)
- [ ] Update arrow doc: status → AUDITED, tests section → audited
- [ ] Update `index.yaml`: status → AUDITED

### Commit message
`audit: puzzle-generation arrow — test annotations, doc cleanup`

---

## Arrow 4: game-lifecycle

**Status:** [x] Complete

### Test files to audit
- `backend/src/test/java/com/sudoku/game/GameServiceImplTest.java`
- `backend/src/test/java/com/sudoku/game/GameResourceTest.java`

### Work items
- [ ] Audit `GameServiceImplTest.java` — verify GL-BE-001 to 022, GL-DATA-001 to 004; add `@spec` annotations
- [ ] Audit `GameResourceTest.java` — verify GL-API-001 to 005; add `@spec` annotations
- [ ] Fix duplicate entry in arrow doc Work Required section (items 1 and 2 are identical)
- [ ] Update arrow doc: status → AUDITED, tests section → audited
- [ ] Update `index.yaml`: status → AUDITED

### Commit message
`audit: game-lifecycle arrow — test annotations, doc cleanup`

---

## Arrow 5: user-management

**Status:** [x] Complete

### Test files to audit
- `backend/src/test/java/com/sudoku/player/PlayerResourceTest.java`
- `backend/src/test/java/com/sudoku/auth/AllowedUsersFilterTest.java`

### Work items
- [ ] Audit `PlayerResourceTest.java` — verify UM-BE-001 to 002, UM-API-001, UM-DATA-001 to 002; add `@spec` annotations
- [ ] Audit `AllowedUsersFilterTest.java` — verify UM-BE-010 to 012, UM-BE-020 to 022; add `@spec` annotations
- [ ] Update arrow doc: status → AUDITED, tests section → audited
- [ ] Update `index.yaml`: status → AUDITED

### Commit message
`audit: user-management arrow — test annotations`

---

## Arrow 6: cloud-platform

**Status:** [x] Complete

### Test files to audit
- No test files exist (no Terratest or equivalent). Note this explicitly in the arrow doc.

### Work items
- [ ] **Must Fix:** Document ECR bootstrap prerequisite — add a note to `infra/README.md` (or create it) explaining that `scripts/bootstrap.sh` must be run before first `terraform apply` (CP-INFRA-012)
- [ ] **Should Fix:** Document source locations for `google_client_id`, `google_client_secret`, `github_token` in `infra/variables.tf` descriptions
- [ ] **Should Fix:** Add CloudWatch alarm resources to `infra/lambda.tf` (or new `infra/monitoring.tf`) for Lambda error rate and throttle metrics with SNS notification
- [ ] Update arrow doc: status → AUDITED, tests section → confirmed no tests exist
- [ ] Update `index.yaml`: status → AUDITED

### Commit message
`audit: cloud-platform arrow — ECR doc, variable descriptions, CloudWatch alarms`

---

## Arrow 7: react-frontend

**Status:** [ ] Complete

### Test files to audit
- No `*.test.jsx` files found under `ui/src/`. Note this explicitly.
- No e2e tests found under `ui/e2e/`. Note this explicitly.

### Work items
- [ ] **Should Fix:** Delete `ui/src/components/GameControls.jsx` (dead stub returning null)
- [ ] **Should Fix:** Verify tutorial markdown — confirmed all 11 files exist in `ui/public/techniques/`; add a comment in `TutorialModal` or a note in the LLD clarifying they are static assets committed to the repo
- [ ] Update arrow doc: status → AUDITED, tests section → confirmed no frontend tests exist (note as gap)
- [ ] Update `index.yaml`: status → AUDITED

### Commit message
`audit: react-frontend arrow — delete GameControls stub, document tutorial assets`

---

## Session Resume Instructions

To resume this plan in a new session:
1. Read this file: `docs/planning/arrow-audit-plan.md`
2. Check the checkbox status to find the next incomplete arrow
3. Read the corresponding arrow doc: `docs/arrows/{arrow-id}.md`
4. Read the test files listed for that arrow
5. Execute the work items in order
6. Commit with the specified message
7. Check the box and move to the next arrow

Current state: **Arrow 1 complete** — begin with Arrow 2: hint-engine.
