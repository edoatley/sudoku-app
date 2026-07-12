// Same grid as naked-single-conversation.js — a real, backend-generated puzzle already
// proven to pass POST /games/from-image's uniqueness re-validation.
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

// Deliberately single-turn: naked-single-conversation.js's second turn ("is that right?")
// has shown a real, reproducible model formatting failure — this scenario isolates the
// simplest possible coach interaction (one question, no history) so the suite still has a
// stable pass/fail signal for the basic "ask -> real non-fallback reply" path.
export default {
  name: 'stuck-single-turn',
  grid: GRID,
  actions: [
    { type: 'hint' },
    { type: 'ask', text: "I'm stuck" },
    { type: 'assert', kind: 'coachFallback', expected: false },
    { type: 'assert', kind: 'hintMatchesCoachTechnique' },
  ],
};
