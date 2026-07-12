// Same base grid as naked-single-conversation.js. Placing 4 at (0,1) duplicates the clue
// 4 already at (0,0) in row 0 — deliberately invalid, to prove the board-validity assertion
// actually distinguishes valid from invalid states (not just always-true).
const GRID = [
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

export default {
  name: 'duplicate-digit-invalid-board',
  grid: GRID,
  actions: [
    { type: 'move', r: 0, c: 1, v: 4 },
    { type: 'sync' },
    { type: 'assert', kind: 'boardValid', expected: false },
  ],
};
