# Arrow: Hint Engine

11 ranked solving strategies with CDI auto-discovery, progressive hint disclosure, and dual-mode validation.

## Status

**OK** - 2026-04-19. All findings from the 2026-04-18 audit resolved.

## References

### HLD
- docs/high-level-design.md — "Data Flow: Hint Request" section

### LLD
- docs/llds/hint-engine.md

### EARS
- docs/specs/hint-engine-specs.md (30 specs, all [x])

### Tests
- backend/src/test/java/com/sudoku/puzzle/SudokuServiceImplTest.java — covers HE-BE-001 to 007, HE-BE-030 to 034; @spec annotations added
- backend/src/test/java/com/sudoku/puzzle/BoardUtilsTest.java — covers SL-PROC-004 to 006; @spec annotations added
- backend/src/test/java/com/sudoku/puzzle/hint/*StrategyTest.java (11 files) — covers HE-BE-010 to 020, HE-API-001 to 006; @spec annotations added
- backend/src/test/java/com/sudoku/puzzle/PuzzleResourceTest.java — covers HTTP mapping of HintResult variants

### Code
- backend/src/main/java/.../puzzle/SudokuService.java
- backend/src/main/java/.../puzzle/SudokuServiceImpl.java
- backend/src/main/java/.../puzzle/hint/HintStrategy.java
- backend/src/main/java/.../puzzle/hint/HintResult.java
- backend/src/main/java/.../puzzle/hint/Difficulty.java
- backend/src/main/java/.../puzzle/hint/*Strategy.java (11 files)
- backend/src/main/java/.../puzzle/hint/BoardUtils.java
- backend/src/main/java/.../puzzle/hint/UnitScanner.java

## Architecture

**Purpose:** Discover and chain 11 Sudoku solving strategies in difficulty order. Return the simplest applicable technique as a structured hint with three disclosure levels.

**Key Components:**
1. `SudokuServiceImpl` — CDI-discovers strategies, sorts by rank, orchestrates getHint/validate/solve/candidates
2. `HintStrategy` interface — evaluate(Board) → Optional<HintResponse>; getDifficultyRank(); getSlug()
3. 11 strategy implementations — ranks 10–110, EASY/MEDIUM/HARD tiers
4. `HintResponse` record — fully populated on every response; frontend controls stage display
5. `HintResult` sealed type — `Found`, `PuzzleSolved`, `NoStrategyApplied`; returned by `getHint()` so callers distinguish all three outcomes
6. `UnitScanner` utility — shared rows→columns→blocks iteration used by 7 unit-based strategies

## EARS Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| Strategy Discovery | HE-BE-001 to 002 | 2 | 0 | 0 |
| Hint Request | HE-BE-003 to 007 | 5 | 0 | 0 |
| Strategy Implementations | HE-BE-010 to 020 | 11 | 0 | 0 |
| Hint Response Structure | HE-API-001 to 006 | 6 | 0 | 0 |
| Supporting Operations | HE-BE-030 to 035 | 6 | 0 | 0 |

**Summary:** 30 of 30 active specs implemented; 0 deferred; 0 gaps.

## Work Required

None.
