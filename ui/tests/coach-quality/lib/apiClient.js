/**
 * Thin client for the backend REST API used by the coach-quality diagnostic runner.
 *
 * Nothing in this suite drives a browser: POST /puzzles/hint and POST /ai/coach both take the
 * board directly in the request body (no persisted state needed), and PATCH /games/{id}'s
 * `events` field is untrusted, client-supplied observability data the backend logs verbatim
 * (see backend/.../game/web/PuzzleEvent.java's own doc comment) — nothing requires it to have
 * come from real UI interaction. So a script can construct events itself and PATCH them in.
 *
 * No Authorization header is sent by default — DevUserFilter (backend/.../developer/DevUserFilter.java,
 * active under the dev/it/test build profiles) injects a fixed mock principal
 * ("local-dev-user") whenever one is absent, regardless of caller. A deployed %gcp/%prod
 * backend has no such filter and enforces real Identity Platform JWT validation, so pointing
 * COACH_QUALITY_API_URL at one also requires COACH_QUALITY_AUTH_TOKEN (a bearer token, e.g.
 * minted via scripts/github/gcp-smoke-token.sh) or every call 401s.
 */
import { request as playwrightRequest } from '@playwright/test';

// Trailing slash required: relative paths are resolved against baseURL with standard URL-join
// rules, where a baseURL missing the trailing slash drops its last path segment ("v1") instead
// of appending to it.
const rawApiUrl = process.env.COACH_QUALITY_API_URL ?? 'http://localhost:8080/api/v1';
const API_URL = rawApiUrl.endsWith('/') ? rawApiUrl : `${rawApiUrl}/`;
const AUTH_TOKEN = process.env.COACH_QUALITY_AUTH_TOKEN;

function emptyCandidatesGrid() {
  return { rows: Array.from({ length: 9 }, () => Array.from({ length: 9 }, () => [])) };
}

export async function createApiClient() {
  const api = await playwrightRequest.newContext({
    baseURL: API_URL,
    extraHTTPHeaders: AUTH_TOKEN ? { Authorization: `Bearer ${AUTH_TOKEN}` } : undefined,
  });

  async function postJson(path, data) {
    const started = Date.now();
    const response = await api.post(path, { data });
    return { response, durationMs: Date.now() - started };
  }

  return {
    /** POST /games/from-image -> GameState */
    async createGameFromGrid(grid) {
      const { response } = await postJson('games/from-image', { originalGrid: { rows: grid } });
      if (!response.ok()) {
        throw new Error(`createGameFromGrid failed (${response.status()}): ${await response.text()}`);
      }
      return response.json();
    },

    /**
     * POST /puzzles/hint -> the endpoint's real three-way contract (PuzzleResource.hint()):
     * 200 = found, 204 = puzzle solved, 404 = no strategy applies at the requested rank.
     */
    async getHint(board, { minRank, excludedRanks } = {}) {
      const { response, durationMs } = await postJson('puzzles/hint', {
        currentGrid: { rows: board },
        minRank: minRank ?? null,
        excludedRanks: excludedRanks ?? null,
      });
      if (response.status() === 204) return { status: 'solved', durationMs };
      if (response.status() === 404) return { status: 'no-strategy', durationMs };
      if (!response.ok()) {
        throw new Error(`getHint failed (${response.status()}): ${await response.text()}`);
      }
      return { status: 'found', hint: await response.json(), durationMs };
    },

    /** POST /ai/coach -> CoachResponse, or null on 204 (puzzle already solved). */
    async askCoach(board, history, userMessage, gameId) {
      const { response, durationMs } = await postJson('ai/coach', {
        board: { rows: board },
        history,
        userMessage,
        gameId,
      });
      if (response.status() === 204) return { body: null, status: 204, durationMs };
      if (!response.ok()) {
        throw new Error(`askCoach failed (${response.status()}): ${await response.text()}`);
      }
      return { body: await response.json(), status: response.status(), durationMs };
    },

    /**
     * PATCH /games/{gameId} — flushes buffered puzzle-play events and persists currentGrid.
     * Returns no body (GameResource.updateGame() -> Response.ok().build()) — call getGame()
     * afterward if you need the persisted state back.
     */
    async syncGame(gameId, { currentGrid, candidates, timeSpentSeconds, isComplete, hintsUsed, events }) {
      const started = Date.now();
      const response = await api.patch(`games/${gameId}`, {
        data: {
          currentGrid: { rows: currentGrid },
          candidates: candidates ?? emptyCandidatesGrid(),
          timeSpentSeconds: timeSpentSeconds ?? 0,
          isComplete: isComplete ?? false,
          hintsUsed: hintsUsed ?? 0,
          events: events ?? [],
        },
      });
      const durationMs = Date.now() - started;
      if (!response.ok()) {
        throw new Error(`syncGame failed (${response.status()}): ${await response.text()}`);
      }
      return { durationMs };
    },

    /** GET /games/{gameId} -> GameState */
    async getGame(gameId) {
      const response = await api.get(`games/${gameId}`);
      if (!response.ok()) {
        throw new Error(`getGame failed (${response.status()}): ${await response.text()}`);
      }
      return response.json();
    },

    async dispose() {
      await api.dispose();
    },
  };
}
