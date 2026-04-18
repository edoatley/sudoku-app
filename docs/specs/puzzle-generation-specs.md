# Puzzle Generation & Validation — EARS Specifications

## Puzzle Generation

- [x] **PG-BE-001**: The system shall generate a valid, fully-solved 9×9 Sudoku solution via randomised recursive backtracking before removing clues.
- [x] **PG-BE-002**: The system shall remove clues one at a time, verifying after each removal that exactly one solution remains, and restore the clue if uniqueness is violated.
- [x] **PG-BE-003**: The system shall target the following clue counts by difficulty: easy=36, medium=30, hard=25, expert=22.
- [x] **PG-BE-004**: When the uniqueness constraint prevents reaching the target clue count, the system shall stop early and return the puzzle with more clues than targeted.
- [x] **PG-BE-005**: The system shall initialise its Random instance at startup (not per-request) to be compatible with Lambda SnapStart.
- [x] **PG-BE-006**: The system shall accept a seeded Random via a package-private constructor to enable deterministic testing.

## REST Endpoints

- [x] **PG-API-001**: The system shall expose GET /puzzles/generate accepting an optional difficulty query parameter defaulting to "medium", requiring no authentication.
- [x] **PG-API-002**: The system shall expose POST /puzzles/validate accepting a BoardRequest and returning isValid, isSolved, and a list of error cell coordinates, requiring no authentication.
- [x] **PG-API-003**: The system shall expose POST /puzzles/hint accepting a BoardRequest and returning a HintResponse, or HTTP 404 if no applicable strategy is found, requiring no authentication.
- [x] **PG-API-004**: The system shall expose POST /puzzles/candidates accepting a BoardRequest and returning the full 9×9 candidate grid, requiring no authentication.

## Developer Endpoints

- [x] **PG-DEV-001**: The system shall expose GET /dev/hint-demo?technique={slug} returning a pre-baked board where the named technique is immediately applicable, along with the technique's rank as minRank.
- [x] **PG-DEV-002**: When the technique parameter is absent or blank, the system shall return HTTP 400.
- [x] **PG-DEV-003**: When the technique slug is not recognised, the system shall return HTTP 404.
- [x] **PG-DEV-004**: The system shall load all hint demo grids from classpath JSON files at class initialisation time, and throw IllegalStateException at startup if any file is missing or malformed.

## DTOs

- [x] **PG-API-010**: The system shall accept currentGrid, optional solutionGrid, optional minRank, and optional excludedRanks in BoardRequest.
- [x] **PG-API-011**: The system shall return originalGrid, solutionGrid, difficulty, and optional minRank in PuzzleResponse.
- [x] **PG-API-012**: The system shall use zero-based (row, col) coordinates in all Coordinate, CoordinateCandidate, and ActionableCell DTOs.
