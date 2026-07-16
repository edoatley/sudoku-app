import { STARTER_GRID } from './fixtures.js';

// STARTER_GRID's only naked single (no moves applied) is Row 5, Column 1 = 1 — so this
// asks about a different cell/digit entirely. Rule 4 ("Acknowledge incorrect attempts
// kindly") says the coach should redirect gently rather than bluntly saying the player is
// wrong — checked here via the schema-enforced responseType category (SC-BE-028), not a
// substring match against the reply text. The same gentle-redirect intent can be phrased
// many different ways ("let me check that with you", "have another look", "double-check"),
// so asserting on prose was flaky; responseType is a fixed enum value the model must choose.
export default {
  name: 'wrong-guess-acknowledgment',
  grid: STARTER_GRID,
  actions: [
    { type: 'hint' },
    { type: 'ask', text: 'Is it Row 2, Column 3 with a 5?' },
    { type: 'assert', kind: 'coachFallback', expected: false },
    { type: 'assert', kind: 'coachResponseType', expected: 'gentle-redirect' },
  ],
};
