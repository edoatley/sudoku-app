/**
 * Reads structured log lines for the backend, selecting the source by target: the local
 * container's stdout via `docker compose logs` by default, or GCP Cloud Logging
 * (cloudLoggingClient.js) when COACH_QUALITY_API_URL points at a deployed Cloud Run backend.
 * Both are the harness equivalent of scripts/logs/download-puzzle-logs.sh /
 * download-coach-logs.sh, which pull the same lines from CloudWatch for AWS. Running against
 * the local stack means no propagation delay; the remote GCP path does have ingestion lag,
 * which waitForCoachPair compensates for with a longer default timeout.
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
import { fetchLogLinesGcp, isRemoteLogSource } from './cloudLoggingClient.js';
import { parseStructuredLine } from './logParse.js';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');
const COMPOSE_ARGS = ['compose', '-f', 'docker-compose.test.yml', '-f', 'docker-compose.coach-quality.yml'];

const COACH_TYPES = new Set(['COACH_REQUEST', 'COACH_RESPONSE']);

// Remote (Cloud Logging) defaults for waitForCoachPair: ingestion lag makes the local 15s/500ms
// too tight. Overridable so a slow/backed-up project can be given more headroom without a code change.
const REMOTE_TIMEOUT_MS = Number(process.env.COACH_QUALITY_LOG_TIMEOUT_MS) || 90_000;
const REMOTE_POLL_MS = Number(process.env.COACH_QUALITY_LOG_POLL_MS) || 5_000;

// @spec CQ-LOG-002, CQ-LOG-003
function fetchLogLines(since) {
  // Deployed GCP backend → read from Cloud Logging instead of the local container's stdout.
  if (isRemoteLogSource()) return fetchLogLinesGcp(since);

  const args = [...COMPOSE_ARGS, 'logs', 'backend', '--no-color'];
  if (since) args.push('--since', since);
  const raw = execFileSync('docker', args, {
    cwd: REPO_ROOT,
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
  });
  const lines = [];
  for (const line of raw.split('\n')) {
    // Each line is "container-name | <timestamp> <LEVEL> [logger] (thread) {json}" — take
    // everything from the first '{' onward (same technique as the download-*.sh scripts).
    const parsed = parseStructuredLine(line);
    if (parsed) lines.push(parsed);
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
 * the HTTP request (see BedrockCoachClient.call / VertexCoachClient.call), so by the time the
 * frontend's `POST /ai/coach` response resolves the pair is already emitted. Locally the poll
 * only needs to cover `docker compose logs`' read latency; against a deployed GCP backend it
 * must also cover Cloud Logging's ingestion lag, so the remote defaults are larger.
 *
 * @param {string} pid gameId
 * @param {number} turnIndex 0-based index of the coach turn within the scenario
 * @param {{timeoutMs?: number, pollIntervalMs?: number, since?: string}} [opts] — timeoutMs /
 *   pollIntervalMs default to 15s/500ms locally and REMOTE_TIMEOUT_MS/REMOTE_POLL_MS against a
 *   deployed backend.
 */
export async function waitForCoachPair(pid, turnIndex, opts = {}) {
  const remote = isRemoteLogSource();
  const timeoutMs = opts.timeoutMs ?? (remote ? REMOTE_TIMEOUT_MS : 15_000);
  const pollIntervalMs = opts.pollIntervalMs ?? (remote ? REMOTE_POLL_MS : 500);
  const { since } = opts;
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
