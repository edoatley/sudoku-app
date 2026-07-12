// Grid captured from a real play session in docs/tests/ai-coach.md ("Attempt 1") — a
// puzzle the backend actually generated and validated as uniquely solvable, so
// POST /games/from-image (which re-validates uniqueness) won't reject it.
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

// NOTE: the second `ask` ("is that right?") has reproduced a real, non-fallback-formatting
// failure (Bedrock replying in prose instead of the mandated JSON envelope) twice in a row
// during development — see the report for this scenario if it fails here again. That's a
// genuine finding for BedrockCoachClient/the system prompt, not a test infra bug.
export default {
  name: 'naked-single-conversation',
  grid: GRID,
  actions: [
    { type: 'move', r: 1, c: 6, v: 4 },
    { type: 'move', r: 8, c: 8, v: 7 },
    { type: 'sync' },
    { type: 'hint' },
    { type: 'ask', text: "I'm stuck" },
    { type: 'assert', kind: 'coachFallback', expected: false },
    { type: 'assert', kind: 'hintMatchesCoachTechnique' },
    { type: 'ask', text: 'is that right?' },
    { type: 'assert', kind: 'coachFallback', expected: false },
    { type: 'sync' },
    { type: 'assert', kind: 'boardValid', expected: true },
  ],
};
