# Arrow: Puzzle Generation & Validation

Randomised puzzle generator, stateless REST endpoints, developer demo infrastructure, and all puzzle-related DTOs.

## Status

**AUDITED** - 2026-04-18. All source files and tests read, annotated, and verified.

## References

### HLD
- docs/high-level-design.md — "Data Flow: New Game" section

### LLD
- docs/llds/puzzle-generation-validation.md

### EARS
- docs/specs/puzzle-generation-specs.md (15 specs, all [x])

### Tests
- backend/src/test/java/com/sudoku/puzzle/PuzzleGeneratorTest.java — covers PG-BE-001 to 006; @spec annotations added
- backend/src/test/java/com/sudoku/puzzle/PuzzleResourceTest.java — covers PG-API-001 to 004, PG-API-010 to 012; @spec annotations added
- backend/src/test/java/com/sudoku/puzzle/developer/MockSudokuServiceTest.java — covers PG-DEV-001 to 004; @spec annotations added
- backend/src/test/java/com/sudoku/puzzle/developer/HintDemoGridsTest.java — covers PG-DEV-002 to 003; @spec annotations added
- backend/src/test/java/com/sudoku/puzzle/developer/DevResourceTest.java — covers PG-DEV-001, PG-DEV-004; @spec annotations added
- backend/src/test/java/com/sudoku/puzzle/developer/PuzzleCandidateFinderTest.java — **deleted** (scratch file, zero assertions, javadoc marked for deletion)

### Code
- backend/src/main/java/.../puzzle/PuzzleGenerator.java
- backend/src/main/java/.../puzzle/PuzzleResource.java
- backend/src/main/java/.../puzzle/developer/DevResource.java
- backend/src/main/java/.../puzzle/developer/HintDemoGrids.java
- backend/src/main/java/.../puzzle/developer/MockSudokuService.java
- backend/src/main/java/.../game/InvalidPuzzleException.java
- backend/src/main/java/.../game/InvalidPuzzleExceptionMapper.java
- backend/src/main/java/.../dto/ (all DTO records)

## Architecture

**Purpose:** Generate valid unique Sudoku puzzles, expose stateless puzzle operations over HTTP, and support developer tooling for hint strategy testing.

**Key Components:**
1. `PuzzleGenerator` — two-phase generation (fill solution + dig holes with uniqueness check); also used as solver
2. `PuzzleResource` — four stateless REST endpoints (/puzzles/generate, /validate, /hint, /candidates)
3. `DevResource` — /dev/hint-demo endpoint returning pre-baked boards for technique testing
4. `HintDemoGrids` — static initialiser loading 11 JSON demo grids from classpath at startup
5. DTOs — `BoardRequest`, `PuzzleResponse`, `ValidationResponse`, `CandidatesResponse`, `HintResponse`, `Coordinate`, `CoordinateCandidate`, `ActionableCell`

## EARS Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| Puzzle Generation | PG-BE-001 to 006 | 6 | 0 | 0 |
| REST Endpoints | PG-API-001 to 004 | 4 | 0 | 0 |
| Developer Endpoints | PG-DEV-001 to 004 | 4 | 0 | 0 |
| DTOs | PG-API-010 to 012 | 3 | 0 | 0 |

**Summary:** 17 of 17 active specs implemented; 0 deferred; 0 gaps.

## Key Findings

1. **Target clue counts are not guarantees** — `digHoles()` stops early if uniqueness cannot be preserved. A generated "easy" puzzle may have more than 36 clues. (PG-BE-004)
2. **Slug-to-rank lookup** — Fixed. `DevResource` now builds a `Map<String, HintStrategy>` from `getSlug()` at construction time. A duplicate slug would throw `IllegalStateException` at startup. (PG-DEV-001)
3. **`solveGrid()` is non-deterministic** — Delegates to `fillBoard()` which randomises digit order. For puzzles with a unique solution this always returns the correct answer, but two calls on the same incomplete grid may return different solutions.
4. **Duplicate validation in mock** — `MockSudokuService.validatePuzzle()` duplicates the validation logic from `SudokuServiceImpl.validateByDuplicates()`. Changes to duplicate semantics require two updates.

## Work Required

### Nice to Have
1. Extract `validateByDuplicates` logic into a shared utility to eliminate the duplication between `SudokuServiceImpl` and `MockSudokuService`.
