import { STARTER_GRID } from './fixtures.js';

// Existing scenarios only exercise 2 conversation turns. Rule 2's escalation ladder
// ("ask before telling", turn 3 = specific nudge, turn 4+ = disclose more directly) is
// never reached beyond turn 2 elsewhere — this scenario pushes to 4 turns of the player
// making no real progress, to check the coach keeps producing real (non-fallback) JSON
// replies as conversation history grows, and to give a transcript for manual review of
// whether the escalation actually gets more specific by turn 4.
export default {
  name: 'deep-escalation-ladder',
  grid: STARTER_GRID,
  actions: [
    { type: 'hint' },
    { type: 'ask', text: "I'm stuck" },
    { type: 'assert', kind: 'coachFallback', expected: false },
    { type: 'ask', text: "I don't get it" },
    { type: 'assert', kind: 'coachFallback', expected: false },
    { type: 'ask', text: 'still not seeing it' },
    { type: 'assert', kind: 'coachFallback', expected: false },
    { type: 'ask', text: 'just help me more' },
    { type: 'assert', kind: 'coachFallback', expected: false },
  ],
};
