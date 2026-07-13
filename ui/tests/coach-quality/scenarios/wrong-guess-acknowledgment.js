import { STARTER_GRID } from './fixtures.js';

// STARTER_GRID's only naked single (no moves applied) is Row 5, Column 1 = 1 — so this
// asks about a different cell/digit entirely. Rule 4 ("Acknowledge incorrect attempts
// kindly") says the coach should redirect gently rather than bluntly saying the player is
// wrong — checked here via a positive substring for a gentle-redirect marker, since the
// runner's coachLogContains only supports "contains", not "does not contain".
export default {
  name: 'wrong-guess-acknowledgment',
  grid: STARTER_GRID,
  actions: [
    { type: 'hint' },
    { type: 'ask', text: 'Is it Row 2, Column 3 with a 5?' },
    { type: 'assert', kind: 'coachFallback', expected: false },
    { type: 'assert', kind: 'coachLogContains', text: 'double-check' },
  ],
};
