import { STARTER_GRID } from './fixtures.js';

// NOTE: the second `ask` ("is that right?") has reproduced a real, non-fallback-formatting
// failure (Bedrock replying in prose instead of the mandated JSON envelope) twice in a row
// during development — see the report for this scenario if it fails here again. That's a
// genuine finding for BedrockCoachClient/the system prompt, not a test infra bug.
export default {
  name: 'naked-single-conversation',
  grid: STARTER_GRID,
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
