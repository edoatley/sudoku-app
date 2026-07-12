/**
 * Interprets a scenario's `actions` array against the real backend (no browser — see
 * apiClient.js) and produces a full trace, written to disk as evidence via report.js.
 *
 * Local state mirrors what the real frontend tracks client-side: the working grid, a
 * move-history stack (for `undo`), and a buffered PuzzleEvent[] that only becomes visible to
 * the backend's structured logs once a `sync` action flushes it — matching the real app's
 * buffer-then-flush behaviour (ui/src/hooks/useEventLog.js), just driven directly instead of
 * through the UI.
 *
 * Assertions never abort the scenario (collect-and-continue): every action still runs, so a
 * failure partway through doesn't cost you the rest of the trace. If an action itself throws
 * (a real infrastructure/API failure, not an assertion mismatch), the scenario stops there but
 * the report is still written with whatever steps completed, plus the error, before re-throwing.
 */
import { createApiClient } from './apiClient.js';
import { waitForCoachPair, logsForGame } from './dockerLogs.js';
import { isBoardValid } from './boardValidity.js';
import { writeReport } from './report.js';

function cloneGrid(grid) {
  return grid.map((row) => [...row]);
}

function newCid() {
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

// @spec CQ-RUN-002
// When waitForCoachPair couldn't produce a pair (204/no Bedrock call, or a real timeout),
// surface that reason explicitly instead of letting assertions silently compare `undefined`.
function logPairIssue(logPair) {
  if (logPair?.error) return `waitForCoachPair failed: ${logPair.error}`;
  if (logPair?.skipped) return logPair.skipped;
  return null;
}

async function evaluateAssertion(action, ctx) {
  switch (action.kind) {
    case 'boardValid': {
      const game = await ctx.api.getGame(ctx.gameId);
      const actual = isBoardValid(game.currentGrid.rows);
      return { pass: actual === action.expected, expected: action.expected, actual };
    }
    case 'coachFallback': {
      const issue = logPairIssue(ctx.lastCoachStep?.logPair);
      if (issue) return { pass: false, expected: action.expected, actual: `<${issue}>` };
      const actual = ctx.lastCoachStep?.logPair?.response?.fallback;
      return { pass: actual === action.expected, expected: action.expected, actual };
    }
    case 'coachLogContains': {
      const haystack = JSON.stringify(ctx.lastCoachStep?.logPair ?? {});
      const actual = haystack.includes(action.text);
      return { pass: actual, expected: `contains "${action.text}"`, actual };
    }
    case 'hintTechnique': {
      const actual =
        ctx.lastHintResult?.status === 'found' ? ctx.lastHintResult.hint.techniqueName : ctx.lastHintResult?.status;
      return {
        pass: typeof actual === 'string' && actual.includes(action.expected),
        expected: action.expected,
        actual,
      };
    }
    case 'hintFound': {
      const actual = ctx.lastHintResult?.status;
      return { pass: actual === action.expected, expected: action.expected, actual };
    }
    case 'hintMatchesCoachTechnique': {
      // Cross-checks two independently-obtained pieces of live state against each other
      // (no static `expected` needed) — the hint engine's own choice of technique vs. the
      // technique the coach call's CONTEXT NOTES were built from for the same board.
      const hintTechnique = ctx.lastHintResult?.status === 'found' ? ctx.lastHintResult.hint.techniqueName : null;
      const issue = logPairIssue(ctx.lastCoachStep?.logPair);
      if (issue) return { pass: false, expected: hintTechnique, actual: `<${issue}>` };
      const coachTechnique = ctx.lastCoachStep?.logPair?.request?.technique ?? null;
      return {
        pass: hintTechnique != null && hintTechnique === coachTechnique,
        expected: hintTechnique,
        actual: coachTechnique,
      };
    }
    default:
      return { pass: false, expected: action.kind, actual: `unknown assert kind "${action.kind}"` };
  }
}

/** @returns {{trace: object, assertionSummary: object, jsonPath: string, mdPath: string}} */
export async function runScenario(scenario) {
  const api = await createApiClient();
  const startedAt = new Date().toISOString();
  const steps = [];

  let gameId = null;
  let originalGrid = null;
  let currentGrid = null;
  let eventBuffer = [];
  const moveHistory = [];
  const coachHistory = [];
  let coachTurnCount = 0;
  let hintsUsed = 0;
  let lastHintResult = null;
  let lastCoachStep = null;
  let scenarioError = null;

  try {
    const created = await api.createGameFromGrid(scenario.grid);
    gameId = created.gameId;
    originalGrid = cloneGrid(scenario.grid);
    currentGrid = cloneGrid(scenario.grid);

    for (const action of scenario.actions) {
      const stepStart = Date.now();
      let result;

      switch (action.type) {
        case 'move': {
          const isGiven = originalGrid[action.r][action.c] !== 0;
          if (isGiven) {
            result = { skipped: true };
          } else {
            const prevValue = currentGrid[action.r][action.c];
            currentGrid[action.r][action.c] = action.v;
            moveHistory.push({ r: action.r, c: action.c, prevValue });
            eventBuffer.push({ type: 'NUMBER', r: action.r, c: action.c, v: action.v, clientTs: Date.now() });
            result = { prevValue };
          }
          break;
        }
        case 'clear': {
          const isGiven = originalGrid[action.r][action.c] !== 0;
          if (isGiven || currentGrid[action.r][action.c] === 0) {
            result = { skipped: true };
          } else {
            const prevValue = currentGrid[action.r][action.c];
            currentGrid[action.r][action.c] = 0;
            moveHistory.push({ r: action.r, c: action.c, prevValue });
            eventBuffer.push({ type: 'NUMBER_CLEAR', r: action.r, c: action.c, clientTs: Date.now() });
            result = { prevValue };
          }
          break;
        }
        case 'undo': {
          const entry = moveHistory.pop();
          if (!entry) {
            result = { skipped: true };
          } else {
            const removed = currentGrid[entry.r][entry.c];
            currentGrid[entry.r][entry.c] = entry.prevValue;
            eventBuffer.push({
              type: 'UNDO',
              r: entry.r,
              c: entry.c,
              v: removed,
              prevV: entry.prevValue,
              undoneType: 'NUMBER',
              clientTs: Date.now(),
            });
            result = { r: entry.r, c: entry.c, removed, restored: entry.prevValue };
          }
          break;
        }
        case 'hint': {
          const cid = newCid();
          const hintResult = await api.getHint(currentGrid, {
            minRank: action.minRank,
            excludedRanks: action.excludedRanks,
          });
          eventBuffer.push({
            type: 'HINT_REQUEST',
            cid,
            minRank: action.minRank ?? null,
            excludedRanks: action.excludedRanks ?? null,
            clientTs: Date.now(),
          });
          if (hintResult.status === 'found') {
            hintsUsed += 1;
            eventBuffer.push({
              type: 'HINT_RESPONSE',
              cid,
              techniqueName: hintResult.hint.techniqueName,
              strategyRank: hintResult.hint.strategyRank,
              difficulty: hintResult.hint.difficulty,
              found: true,
              clientTs: Date.now(),
            });
          } else {
            eventBuffer.push({ type: 'HINT_RESPONSE', cid, found: false, clientTs: Date.now() });
          }
          lastHintResult = hintResult;
          result = hintResult;
          break;
        }
        case 'ask': {
          // Snapshot before mutating, matching useCoachSession.js's pre-push apiHistory —
          // and cap at 6 like the real frontend does (the backend trims to the same limit
          // anyway, but this keeps the request payload faithful to real client behaviour).
          const historyForRequest = coachHistory.slice(-6);
          const apiResponse = await api.askCoach(currentGrid, historyForRequest, action.text, gameId);
          coachHistory.push({ role: 'user', content: action.text });
          if (apiResponse.body) {
            coachHistory.push({ role: 'assistant', content: apiResponse.body.aiMessage });
          }
          // @spec CQ-RUN-001
          let logPair;
          if (apiResponse.status === 204) {
            // Puzzle already solved — the backend returns 204 without ever calling Bedrock
            // (SudokuCoachServiceImpl's PuzzleSolved branch), so no COACH_REQUEST/COACH_RESPONSE
            // pair will ever appear for this turn. Don't wait for one, and don't consume a
            // turn index — coachPairsForGame only contains pairs from turns that actually
            // reached Bedrock, so incrementing here would desync every later `ask` in the scenario.
            logPair = { skipped: 'puzzle already solved (204) — no Bedrock call was made' };
          } else {
            const turnIndex = coachTurnCount++;
            try {
              logPair = await waitForCoachPair(gameId, turnIndex, { since: startedAt });
            } catch (err) {
              logPair = { error: err.message };
            }
          }
          result = { apiResponse: apiResponse.body, status: apiResponse.status, logPair };
          lastCoachStep = result;
          break;
        }
        case 'sync': {
          const elapsedSeconds = Math.round((Date.now() - Date.parse(startedAt)) / 1000);
          const flushedCount = eventBuffer.length;
          await api.syncGame(gameId, {
            currentGrid,
            timeSpentSeconds: elapsedSeconds,
            hintsUsed,
            events: eventBuffer,
          });
          eventBuffer = [];
          // PATCH returns no body — re-fetch so the trace shows the actual persisted state
          // after this sync, not just "we sent a request".
          const gameState = await api.getGame(gameId);
          result = { gameState, eventsFlushed: flushedCount };
          break;
        }
        case 'assert': {
          result = await evaluateAssertion(action, { api, gameId, lastHintResult, lastCoachStep });
          break;
        }
        default:
          throw new Error(`Unknown action type: "${action.type}"`);
      }

      steps.push({ index: steps.length, action, durationMs: Date.now() - stepStart, result });
    }
  } catch (err) {
    scenarioError = { message: err.message, stack: err.stack };
  }

  // @spec CQ-RPT-001
  // Always fetch the persisted state and full log stream for the report, even if the
  // scenario never issued its own `sync` action or errored partway through.
  let finalGameState = null;
  let finalGameStateError = null;
  if (gameId) {
    try {
      finalGameState = await api.getGame(gameId);
    } catch (err) {
      finalGameStateError = err.message;
    }
  }
  const finalLogs = gameId ? logsForGame(gameId, startedAt) : [];

  const assertionSteps = steps.filter((s) => s.action.type === 'assert');
  const assertionSummary = {
    total: assertionSteps.length,
    passed: assertionSteps.filter((s) => s.result.pass).length,
    failed: assertionSteps.filter((s) => !s.result.pass).length,
    failures: assertionSteps
      .filter((s) => !s.result.pass)
      .map((s) => ({ kind: s.action.kind, expected: s.result.expected, actual: s.result.actual })),
  };

  const trace = {
    scenario: scenario.name,
    startedAt,
    finishedAt: new Date().toISOString(),
    gameId,
    initialGrid: originalGrid,
    steps,
    finalGameState,
    finalGameStateError,
    finalLogs,
    assertionSummary,
    error: scenarioError,
  };

  const { jsonPath, mdPath } = writeReport(trace);
  await api.dispose();

  if (scenarioError) {
    throw new Error(
      `runScenario("${scenario.name}") failed: ${scenarioError.message}\nFull trace written to ${jsonPath}`
    );
  }

  return { trace, assertionSummary, jsonPath, mdPath };
}
