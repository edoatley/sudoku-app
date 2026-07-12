// @spec CQ-SCN-001
// Grid captured from a real play session in docs/tests/ai-coach.md ("Attempt 1") — a
// puzzle the backend actually generated and validated as uniquely solvable, so
// POST /games/from-image (which re-validates uniqueness) won't reject it. Shared by every
// scenario that needs a real, valid starting board.
export const STARTER_GRID = [
  [4, 0, 0, 0, 0, 0, 6, 8, 0],
  [0, 9, 0, 0, 0, 8, 0, 0, 0],
  [5, 0, 8, 9, 0, 4, 7, 3, 0],
  [6, 2, 0, 0, 0, 0, 8, 0, 0],
  [0, 4, 0, 0, 7, 0, 5, 0, 3],
  [0, 8, 7, 0, 0, 5, 9, 0, 6],
  [8, 0, 0, 0, 0, 0, 0, 5, 4],
  [9, 3, 0, 5, 4, 7, 0, 0, 0],
  [2, 0, 4, 0, 8, 0, 1, 9, 0],
];
