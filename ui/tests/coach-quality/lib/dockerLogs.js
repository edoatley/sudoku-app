/**
 * Reads structured log lines straight from the backend container's stdout via
 * `docker compose logs` — the local equivalent of scripts/logs/download-puzzle-logs.sh /
 * download-coach-logs.sh, which pull the same lines from CloudWatch for a deployed
 * environment. Running locally means no CloudWatch propagation delay.
 *
 * Every structured event (NUMBER, NUMBER_RESULT, NUMBER_CLEAR, UNDO, HINT_REQUEST,
 * HINT_RESPONSE, COACH_REQUEST, COACH_RESPONSE) shares a `pid` (gameId) — see
 * backend/.../game/PuzzleEventLogger.java and backend/.../coach/bedrock/BedrockCoachClient.java.
 * `logsForGame` returns all of them for a pid, chronologically, and is the primary source of
 * "what actually happened" evidence attached to a scenario's report.
 *
 * Coach-turn correlation: the backend generates a `cid` per coach turn but never returns it
 * to the caller, so a turn can't be matched by id. Instead, COACH_REQUEST/COACH_RESPONSE lines
 * sharing a `pid` are paired FIFO (oldest outstanding request first), and the Nth pair for a
 * game corresponds to the Nth `ask` action that actually invoked Bedrock (204/puzzle-solved
 * turns never produce a pair and don't consume a turn index — see runner.js) — valid because
 * the runner executes one scenario's actions strictly serially.
 */
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');
const COMPOSE_ARGS = ['compose', '-f', 'docker-compose.test.yml', '-f', 'docker-compose.coach-quality.yml'];

const COACH_TYPES = new Set(['COACH_REQUEST', 'COACH_RESPONSE']);

// @spec CQ-LOG-002
function fetchLogLines(since) {
  const args = [...COMPOSE_ARGS, 'logs', 'backend', '--no-color'];
  if (since) args.push('--since', since);
  const raw = execFileSync('docker', args, {
    cwd: REPO_ROOT,
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
  });
  const lines = [];
  for (const line of raw.split('\n')) {
    // Each line is "container-name | <timestamp> <LEVEL> [logger] (thread) {json}" —
    // take everything from the first '{' onward, same technique as the download-*.sh
    // scripts, rather than assuming the line is bare JSON.
    const start = line.indexOf('{');
    if (start === -1) continue;
    try {
      lines.push(JSON.parse(line.slice(start)));
    } catch {
      // Not a JSON log line (e.g. a stack trace) — skip.
    }
  }
  return lines;
}

/**
 * Every structured log line for one game (pid), in chronological order.
 * @param {string} pid gameId
 * @param {string} [since] RFC3339 timestamp — bounds the `docker compose logs` read to lines
 *   emitted at or after this point (typically a scenario's `startedAt`) instead of scanning
 *   the backend container's entire log history on every call.
 */
export function logsForGame(pid, since) {
  return fetchLogLines(since).filter((e) => e.pid === pid);
}

// @spec CQ-LOG-001
/** Chronologically-ordered COACH_REQUEST/COACH_RESPONSE pairs for one game (pid). */
function coachPairsForGame(pid, since) {
  const events = logsForGame(pid, since).filter((e) => COACH_TYPES.has(e.type));
  const pairs = [];
  const pendingRequests = [];
  for (const event of events) {
    if (event.type === 'COACH_REQUEST') {
      pendingRequests.push(event);
    } else if (event.type === 'COACH_RESPONSE' && pendingRequests.length > 0) {
      pairs.push({ request: pendingRequests.shift(), response: event });
    }
  }
  return pairs;
}

/**
 * Polls backend logs until the (turnIndex + 1)th COACH_REQUEST/COACH_RESPONSE pair for
 * `pid` has appeared, then returns it. Coach log lines are written synchronously inside
 * the HTTP request (see BedrockCoachClient.call), so by the time the frontend's
 * `POST /ai/coach` response resolves the pair is already in the container's log stream —
 * this poll only needs to cover `docker compose logs`' own read latency.
 *
 * @param {string} pid gameId
 * @param {number} turnIndex 0-based index of the coach turn within the scenario
 * @param {{timeoutMs?: number, pollIntervalMs?: number, since?: string}} [opts]
 */
export async function waitForCoachPair(pid, turnIndex, { timeoutMs = 15_000, pollIntervalMs = 500, since } = {}) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const pairs = coachPairsForGame(pid, since);
    if (pairs.length > turnIndex) {
      return pairs[turnIndex];
    }
    if (Date.now() >= deadline) {
      throw new Error(
        `waitForCoachPair: timed out waiting for coach turn ${turnIndex} for pid=${pid} ` +
          `(found ${pairs.length} pair(s) after ${timeoutMs}ms)`
      );
    }
    await new Promise((resolve) => setTimeout(resolve, pollIntervalMs));
  }
}
