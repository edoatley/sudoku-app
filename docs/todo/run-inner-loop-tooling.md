# Run Inner-Loop Tooling and Fix Issues

**Summary:** Standardise on Biome as the single frontend linter/formatter (replacing ESLint in CI), fix the ~200 Biome findings, then wire up the remaining Makefile targets (Ruff, Trivy, Checkov).

**Branch context:** `rc-rename-ai-scan-endpoint` — rename of `/ai/scan` → `/ai/image-to-puzzle` endpoint

## Why deferred

Pre-commit hooks and Makefile added during this session but never run. The ESLint/Biome overlap was identified as a risk — two tools linting the same files with different rules will diverge silently.

## Recommendation: replace ESLint with Biome in CI

Biome (v2.5.2, installed via Homebrew) already covers the React rules ESLint was providing, is ~35x faster, and handles formatting too. Running both is pure overhead with conflicting signals.

## Current state

`biome check .` run from `ui/` on 2026-07-07 found **123 errors, 82 warnings** — but Biome is currently scanning `dist/` (no `biome.json` to exclude it), so the real number is lower. Actual source findings by rule:

| Rule | Count | Auto-fixable |
|---|---|---|
| `lint/a11y/useButtonType` | 5 | No — test files only |
| `lint/suspicious/noArrayIndexKey` | 4 | No — may be real bugs |
| `lint/a11y/noSvgWithoutTitle` | 4 | No — `public/` SVGs |
| `lint/style/useTemplate` | 2 | Yes |
| `lint/correctness/useJsxKeyInIterable` | 2 | No — real bugs |
| `lint/correctness/useExhaustiveDependencies` | 1 | Fixable |
| `lint/complexity/useLiteralKeys` | 1 | Yes |

Formatting differences unknown until `biome check --write` is run (will reformat to Biome defaults).

## Areas to investigate before committing

1. **`noUnusedVariables` vs ESLint's `varsIgnorePattern: ^[A-Z_]`**
   ESLint ignores uppercase constants (e.g. `CANNED_PUZZLES`) that are imported but only used in mock paths. Biome's `noUnusedVariables` only ignores `_`-prefixed names. Check whether any uppercase imports currently pass ESLint but would fail Biome — if so, configure `biome.json` to suppress or restructure the imports.

2. **The three disabled `react-hooks` rules**
   ESLint has `react-hooks/refs`, `react-hooks/immutability`, and `react-hooks/set-state-in-effect` explicitly disabled (see `ui/eslint.config.js`) because they fire on the "latest-value ref" pattern in `useSudokuGame`. Check whether Biome has equivalents (search `biome explain` for `ref`, `immutability`). If so, they'll need suppressing too.

3. **`noArrayIndexKey` findings — fix or suppress?**
   4 instances in `SudokuGrid.jsx`, `DevDataDialog.jsx`, `CoachPanel.jsx`. Array index keys are a real correctness issue when list order can change — worth fixing rather than suppressing. Investigate whether these lists are ever reordered.

4. **`useJsxKeyInIterable` — 2 instances in `NumberPad.jsx`**
   Missing `key` props on iterated JSX — these are real bugs that ESLint was missing. Fix them.

5. **Formatting changes**
   Run `biome check --write` on a scratch branch and review the diff. Biome defaults: tabs, double quotes, trailing commas. Check whether the resulting format conflicts with anything (Playwright snapshots, snapshot tests, editor config).

## What to do

1. Create `ui/biome.json` — at minimum set `files.ignore` to exclude `dist/`, `playwright-report/`, `test-results/`, `coverage/`. Configure indent and quote style to match current code.
2. Run `biome check --write` — apply auto-fixes (`useTemplate`, `useLiteralKeys`, `useExhaustiveDependencies`), review formatting diff.
3. Investigate and resolve the five areas above.
4. Fix `useJsxKeyInIterable` (2 real bugs in `NumberPad.jsx`).
5. Replace `npm run lint` in `ui/package.json` scripts with `biome check .` and remove `eslint`, `eslint-plugin-react-hooks`, `eslint-plugin-react-refresh`, `@eslint/js`, `globals` from `package.json`.
6. Update `scripts/local/local-alltests.sh` — replace the ESLint step with `biome check .`.
7. Run `ruff check --fix image_recognition/ && ruff format image_recognition/` — fix Python findings.
8. Run `make secure` (Trivy + Checkov) — document or fix any findings.
9. Run `pre-commit run --all-files` — confirm all hooks pass.

## Acceptance criteria

- [ ] `biome check .` exits 0 from `ui/`
- [ ] `pre-commit run --all-files` exits 0
- [ ] `make lint` exits 0
- [ ] `make secure` exits 0 or all findings have documented suppressions
- [ ] ESLint and its plugins removed from `package.json`
- [ ] `scripts/local/local-alltests.sh` uses Biome, not ESLint

## Related specs / docs

- `docs/llds/react-frontend.md` — frontend coding standards
- `ui/eslint.config.js` — the three disabled rules and their justification comments must be preserved as Biome suppressions
