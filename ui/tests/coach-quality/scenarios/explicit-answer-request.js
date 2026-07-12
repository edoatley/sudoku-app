import { STARTER_GRID } from './fixtures.js';

// The system prompt's own Example C (BedrockCoachClient.SYSTEM_PROMPT) uses this near-verbatim
// phrase as its worked example of "player explicitly asks for the answer -> set revealHint
// true" — about as reliable a single-turn behavioural check as this suite can make.
export default {
  name: 'explicit-answer-request',
  grid: STARTER_GRID,
  actions: [
    { type: 'ask', text: 'Just tell me the answer, I give up.' },
    { type: 'assert', kind: 'coachFallback', expected: false },
    { type: 'assert', kind: 'coachLogContains', text: '"revealHint":true' },
  ],
};
