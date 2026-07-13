import { STARTER_GRID } from './fixtures.js';

// Rule 5 ("Stay on topic") says the coach should redirect briefly and warmly rather than
// engage with an unrelated question — this scenario checks it doesn't fall back (a real,
// on-brand reply) and that the reply actually redirects back to the puzzle.
export default {
  name: 'off-topic-message',
  grid: STARTER_GRID,
  actions: [
    { type: 'hint' },
    { type: 'ask', text: 'Who won the last football World Cup?' },
    { type: 'assert', kind: 'coachFallback', expected: false },
    { type: 'assert', kind: 'coachLogContains', text: 'puzzle' },
  ],
};
