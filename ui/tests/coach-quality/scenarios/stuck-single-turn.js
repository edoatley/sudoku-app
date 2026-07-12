import { STARTER_GRID } from './fixtures.js';

// Deliberately single-turn: naked-single-conversation.js's second turn ("is that right?")
// has shown a real, reproducible model formatting failure — this scenario isolates the
// simplest possible coach interaction (one question, no history) so the suite still has a
// stable pass/fail signal for the basic "ask -> real non-fallback reply" path.
export default {
  name: 'stuck-single-turn',
  grid: STARTER_GRID,
  actions: [
    { type: 'hint' },
    { type: 'ask', text: "I'm stuck" },
    { type: 'assert', kind: 'coachFallback', expected: false },
    { type: 'assert', kind: 'hintMatchesCoachTechnique' },
  ],
};
