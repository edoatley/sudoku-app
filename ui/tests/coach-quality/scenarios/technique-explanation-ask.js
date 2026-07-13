import { STARTER_GRID } from './fixtures.js';

// Mirrors the system prompt's own Example D ("What is a hidden single?") — STARTER_GRID's
// first available technique (no moves applied) is Naked Single, so the question matches
// that rather than hardcoding "hidden single". The coach should explain the concept using
// its CONTEXT NOTES rather than going off-script.
export default {
  name: 'technique-explanation-ask',
  grid: STARTER_GRID,
  actions: [
    { type: 'hint' },
    { type: 'ask', text: 'What is a naked single?' },
    { type: 'assert', kind: 'coachFallback', expected: false },
    { type: 'assert', kind: 'hintMatchesCoachTechnique' },
  ],
};
