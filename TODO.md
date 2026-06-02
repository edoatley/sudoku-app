# TODO

> Last updated: 2026-05-31
> Sources scanned: README.md § TODO, docs/specs/*.md (EARS), docs/arrows/index.yaml, backend/src/**/*.java, ui/src/**/*.{jsx,js}, image_recognition/**/*.py, infra/**/*.tf

---

## Features

- [ ] More strategies: XY-Chain, Remote Pairs (README.md#L210)
- [ ] Manual puzzle entry mode — fallback when import fails (README.md#L211)
- [x] League table — rank players by puzzles completed and time taken (README.md#L213)
- [ ] Add expert difficulty level — backend complete (PuzzleGenerator supports it); UI NewGameModal needs expert option added (README.md#L214)

## Error Handling

- [ ] Improved error handling — user-friendly errors, not 422/404; better error visuals; no React ErrorBoundary yet (README.md#L209)

## Infrastructure

- [ ] Terratest for infrastructure — automated infra test suite (README.md#L215)
- [ ] Evaluate AWS CDK as an alternative to Terraform — spike to assess DX, type safety, and migration cost

## Tech Debt

- [ ] UI cleanup: rationalise directory structure (README.md#L219)

---

## Deferred

> Items below are intentionally parked. Each carries a blocking note explaining the
> re-evaluation trigger. Do not move items here without a recorded reason.

- [ ] IR-PROC-001–005: PIL image preprocessing pipeline — downscale, alpha composite, desaturate, JPEG encode, fallback (docs/specs/image-recognition-specs.md#IR-PROC-001, docs/arrows/index.yaml#image-recognition)
  - **Blocked**: Re-evaluate only after solving colour-cell desaturation problem; multi-model cascade tested and rejected (2026-04-20)

---

## Completed

- [x] Score history improvements — PuzzleHistoryDialog with SummaryBanner, win rate, best time, streak, avg score, difficulty-coloured GameCards (README.md#L212)
- [x] AEH-EX-008: DynamoDbGameRepository.findById() throws GameNotFoundException — verified, empty item triggers explicit throw (docs/specs/api-error-handling-specs.md#AEH-EX-008)
- [x] AEH-EX-009: DynamoDbGameRepository.update() throws GameNotFoundException — verified, not a silent no-op (docs/specs/api-error-handling-specs.md#AEH-EX-009)
- [x] UI cleanup: persist game history to server — usePlayerProfile fetches from /games/history; localStorage used as cache only (README.md#L217)
- [x] UI cleanup: decompose `useSudokuGame` into smaller focused hooks — extracted `useGameTimer`, `useHintSystem`, `useGameSync`; public API unchanged (#86)
