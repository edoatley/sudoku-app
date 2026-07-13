/**
 * AI coach quality suite — a diagnostic runner, not a conventional test suite. Drives the
 * real backend + real DynamoDB Local + real Bedrock (see docker-compose.coach-quality.yml)
 * directly via the REST API (no browser — see lib/apiClient.js for why nothing here needs
 * one), and writes a full trace of every action, request/response, and correlated structured
 * log line to ui/tests/coach-quality/reports/ for manual analysis — that report is the primary
 * deliverable, produced whether or not a scenario's assertions all pass.
 *
 * Opt-in only (not part of scripts/local/local-alltests.sh): calls a real LLM, so replies
 * are non-deterministic prose, cost real (small) tokens, and need AWS credentials.
 *
 * Run with: bash scripts/local/coach-quality-test.sh
 *
 * Scenarios live in ./scenarios/*.js as an ordered action array — see README.md for the full
 * action/assertion reference and how to add one.
 */
import { test, expect } from '@playwright/test';
import { runScenario } from './lib/runner.js';

import naked_single_conversation from './scenarios/naked-single-conversation.js';
import duplicate_digit_invalid_board from './scenarios/duplicate-digit-invalid-board.js';
import stuck_single_turn from './scenarios/stuck-single-turn.js';
import explicit_answer_request from './scenarios/explicit-answer-request.js';
import off_topic_message from './scenarios/off-topic-message.js';
import wrong_guess_acknowledgment from './scenarios/wrong-guess-acknowledgment.js';
import deep_escalation_ladder from './scenarios/deep-escalation-ladder.js';
import technique_explanation_ask from './scenarios/technique-explanation-ask.js';

const SCENARIOS = [
  naked_single_conversation,
  duplicate_digit_invalid_board,
  stuck_single_turn,
  explicit_answer_request,
  off_topic_message,
  wrong_guess_acknowledgment,
  deep_escalation_ladder,
  technique_explanation_ask,
];

for (const scenario of SCENARIOS) {
  test(`coach quality — ${scenario.name}`, async () => {
    const { assertionSummary, jsonPath, mdPath } = await runScenario(scenario);

    // The report is already on disk regardless of outcome (runScenario writes it
    // unconditionally) — this is the only thing that makes the test itself pass/fail.
    expect(
      assertionSummary.failed,
      `${assertionSummary.failed}/${assertionSummary.total} assertion(s) failed — see:\n` +
        `  ${jsonPath}\n  ${mdPath}\n` +
        JSON.stringify(assertionSummary.failures, null, 2)
    ).toBe(0);
  });
}
