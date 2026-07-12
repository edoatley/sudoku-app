import { STARTER_GRID } from './fixtures.js';

// Placing 4 at (0,1) duplicates the clue 4 already at (0,0) in row 0 — deliberately invalid,
// to prove the board-validity assertion actually distinguishes valid from invalid states
// (not just always-true).
export default {
  name: 'duplicate-digit-invalid-board',
  grid: STARTER_GRID,
  actions: [
    { type: 'move', r: 0, c: 1, v: 4 },
    { type: 'sync' },
    { type: 'assert', kind: 'boardValid', expected: false },
  ],
};
