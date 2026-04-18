# Arrow: Hint Engine

11 ranked solving strategies with CDI auto-discovery, progressive hint disclosure, and dual-mode validation.

## Status

**AUDITED** - 2026-04-18. All strategy implementations, orchestrator, and tests read, annotated, and verified.

## References

### HLD
- docs/high-level-design.md — "Data Flow: Hint Request" section

### LLD
- docs/llds/hint-engine.md

### EARS
- docs/specs/hint-engine-specs.md (29 specs, all [x])

### Tests
- backend/src/test/java/com/sudoku/puzzle/SudokuServiceImplTest.java — covers HE-BE-001 to 007, HE-BE-030 to 034; @spec annotations added
- backend/src/test/java/com/sudoku/puzzle/hint/BoardUtilsTest.java — covers SL-PROC-004 to 006; @spec annotations added
- backend/src/test/java/com/sudoku/puzzle/hint/*StrategyTest.java (11 files) — covers HE-BE-010 to 020, HE-API-001 to 006; @spec annotations added

### Code
- backend/src/main/java/.../puzzle/SudokuService.java
- backend/src/main/java/.../puzzle/SudokuServiceImpl.java
- backend/src/main/java/.../puzzle/hint/HintStrategy.java
- backend/src/main/java/.../puzzle/hint/Difficulty.java
- backend/src/main/java/.../puzzle/hint/*Strategy.java (11 files)
- backend/src/main/java/.../puzzle/hint/BoardUtils.java

## Architecture

**Purpose:** Discover and chain 11 Sudoku solving strategies in difficulty order. Return the simplest applicable technique as a structured hint with three disclosure levels.

**Key Components:**
1. `SudokuServiceImpl` — CDI-discovers strategies, sorts by rank, orchestrates getHint/validate/solve/candidates
2. `HintStrategy` interface — evaluate(Board) → Optional<HintResponse>; getDifficultyRank(); getSlug()
3. 11 strategy implementations — ranks 10–110, EASY/MEDIUM/HARD tiers
4. `HintResponse` record — fully populated on every response; frontend controls stage display

## EARS Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| Strategy Discovery | HE-BE-001 to 002 | 2 | 0 | 0 |
| Hint Request | HE-BE-003 to 007 | 5 | 0 | 0 |
| Strategy Implementations | HE-BE-010 to 020 | 11 | 0 | 0 |
| Hint Response Structure | HE-API-001 to 006 | 6 | 0 | 0 |
| Supporting Operations | HE-BE-030 to 035 | 6 | 0 | 0 |

**Summary:** 30 of 30 active specs implemented; 0 deferred; 0 gaps.

## Key Findings

1. **Naked Pair label mismatch** — NakedPairStrategy has difficulty=MEDIUM but rank=30, which is below HiddenSingle rank=40 (EASY). Rank ordering is authoritative; the enum label is misleading.
2. **`Optional.empty()` ambiguity** — `getHint()` returns empty both when the puzzle is already solved and when all strategies are excluded by minRank/excludedRanks. Callers cannot distinguish these cases.
3. **Unit-scanning boilerplate** — All 11 strategies independently implement the rows→columns→blocks scanning loop. A shared `UnitScanner` abstraction does not exist.
4. **Test constructor in production class** — `SudokuServiceImpl` has package-private constructors for injecting mock strategies without CDI. Functional but non-production code in production class.

## Work Required

### Done
1. ~~Document the Naked Pair rank vs label discrepancy~~. Added explanatory comment in `NakedPairStrategy.java` getDifficulty() block. (HE-BE-012)

### Nice to Have
2. Return a typed result from `getHint()` distinguishing "no hint found" from "puzzle already solved" to allow callers to handle each case correctly. (HE-BE-007)
3. Extract shared unit-scanning loop into a `UnitScanner` utility to reduce boilerplate across strategy implementations.
