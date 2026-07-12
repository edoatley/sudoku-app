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

// The system prompt's own Example C (BedrockCoachClient.SYSTEM_PROMPT) uses this near-verbatim
// phrase as its worked example of "player explicitly asks for the answer -> set revealHint
// true" — about as reliable a single-turn behavioural check as this suite can make.
export default {
  name: 'explicit-answer-request',
  grid: GRID,
  actions: [
    { type: 'ask', text: 'Just tell me the answer, I give up.' },
    { type: 'assert', kind: 'coachFallback', expected: false },
    { type: 'assert', kind: 'coachLogContains', text: '"revealHint":true' },
  ],
};
