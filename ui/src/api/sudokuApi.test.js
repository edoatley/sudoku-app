import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  CANNED_PUZZLES,
  CANNED_VALIDATE_VALID,
  CANNED_HINT,
  CANNED_CANDIDATES,
  CANNED_GAME_STATE,
} from '../mocks/cannedData.js';

// Mock aws-amplify/auth so authenticated endpoints don't fail in tests
vi.mock('aws-amplify/auth', () => ({
  fetchAuthSession: vi.fn().mockResolvedValue({
    tokens: { idToken: { toString: () => 'test-id-token' } },
  }),
}));

// ─── Mock mode ────────────────────────────────────────────────────────────────

describe('sudokuApi — mock mode (VITE_MOCK_API=true)', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubEnv('VITE_MOCK_API', 'true');
    vi.stubEnv('VITE_API_URL', 'http://test-api/v1');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('generatePuzzle returns plain-array originalGrid without calling fetch', async () => {
    const { generatePuzzle } = await import('./sudokuApi.js');
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const result = await generatePuzzle('easy');
    // Mock data is unwrapped from {rows:[...]} wire format to plain array
    expect(result.originalGrid).toEqual(CANNED_PUZZLES.easy.originalGrid.rows);
    expect(result.difficulty).toBe('easy');
    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });

  it('generatePuzzle returns easy data for unknown difficulty', async () => {
    const { generatePuzzle } = await import('./sudokuApi.js');
    const result = await generatePuzzle('unknown');
    expect(result.originalGrid).toEqual(CANNED_PUZZLES.easy.originalGrid.rows);
  });

  it('generatePuzzle returns correct data for medium difficulty', async () => {
    const { generatePuzzle } = await import('./sudokuApi.js');
    const result = await generatePuzzle('medium');
    expect(result.originalGrid).toEqual(CANNED_PUZZLES.medium.originalGrid.rows);
  });

  it('validatePuzzle returns canned valid result', async () => {
    const { validatePuzzle } = await import('./sudokuApi.js');
    const result = await validatePuzzle([[]]);
    expect(result).toEqual(CANNED_VALIDATE_VALID);
  });

  it('getHint returns canned hint', async () => {
    const { getHint } = await import('./sudokuApi.js');
    const result = await getHint([[]]);
    expect(result).toEqual(CANNED_HINT);
  });

  it('getCandidates returns canned candidates', async () => {
    const { getCandidates } = await import('./sudokuApi.js');
    const result = await getCandidates([[]]);
    expect(result).toEqual(CANNED_CANDIDATES);
  });

  // @spec GH-UI-002
  it('getGameHistory returns empty array without calling fetch in mock mode', async () => {
    const { getGameHistory } = await import('./sudokuApi.js');
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const result = await getGameHistory();
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(result).toEqual([]);
    fetchSpy.mockRestore();
  });

  it('createGame returns plain-array grids with the requested difficulty', async () => {
    const { createGame } = await import('./sudokuApi.js');
    const result = await createGame('hard');
    expect(result.difficulty).toBe('hard');
    expect(result.gameId).toBe(CANNED_GAME_STATE.gameId);
    // Grids are plain arrays, not {rows:[...]} objects
    expect(Array.isArray(result.originalGrid)).toBe(true);
    expect(Array.isArray(result.currentGrid)).toBe(true);
    expect(Array.isArray(result.candidates)).toBe(true);
  });

  it('loadGame returns plain-array grids with the requested gameId', async () => {
    const { loadGame } = await import('./sudokuApi.js');
    const result = await loadGame('abc-123');
    expect(result.gameId).toBe('abc-123');
    expect(Array.isArray(result.originalGrid)).toBe(true);
    expect(Array.isArray(result.currentGrid)).toBe(true);
  });

  it('saveGame resolves without calling fetch', async () => {
    const { saveGame } = await import('./sudokuApi.js');
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    await expect(
      saveGame('abc-123', { currentGrid: [], candidates: [], timeSpentSeconds: 0 })
    ).resolves.toBeUndefined();
    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });
});

// ─── Real mode ────────────────────────────────────────────────────────────────

describe('sudokuApi — real mode (VITE_MOCK_API=false)', () => {
  let fetchSpy;

  beforeEach(() => {
    vi.resetModules();
    vi.stubEnv('VITE_MOCK_API', 'false');
    vi.stubEnv('VITE_SKIP_AUTH', 'false');
    vi.stubEnv('VITE_API_URL', 'http://test-api/v1');
    fetchSpy = vi.spyOn(globalThis, 'fetch');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it('generatePuzzle GETs the correct endpoint', async () => {
    fetchSpy.mockResolvedValue(new Response(JSON.stringify({ originalGrid: { rows: [] } }), { status: 200 }));
    const { generatePuzzle } = await import('./sudokuApi.js');
    await generatePuzzle('medium');
    expect(fetchSpy).toHaveBeenCalledWith('http://test-api/v1/puzzles/generate?difficulty=medium', expect.any(Object));
  });

  it('generatePuzzle unwraps originalGrid from wire format', async () => {
    const wireRows = [
      [1, 2, 3],
      [4, 5, 6],
      [7, 8, 9],
    ];
    fetchSpy.mockResolvedValue(
      new Response(JSON.stringify({ originalGrid: { rows: wireRows }, difficulty: 'easy' }), { status: 200 })
    );
    const { generatePuzzle } = await import('./sudokuApi.js');
    const result = await generatePuzzle('easy');
    expect(result.originalGrid).toEqual(wireRows);
  });

  it('validatePuzzle POSTs to the correct endpoint', async () => {
    fetchSpy.mockResolvedValue(new Response(JSON.stringify(CANNED_VALIDATE_VALID), { status: 200 }));
    const { validatePuzzle } = await import('./sudokuApi.js');
    await validatePuzzle(Array(9).fill(Array(9).fill(0)));
    expect(fetchSpy).toHaveBeenCalledWith(
      'http://test-api/v1/puzzles/validate',
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('validatePuzzle wraps currentGrid in wire format', async () => {
    fetchSpy.mockResolvedValue(new Response(JSON.stringify(CANNED_VALIDATE_VALID), { status: 200 }));
    const { validatePuzzle } = await import('./sudokuApi.js');
    const grid = Array(9)
      .fill(null)
      .map(() => Array(9).fill(0));
    await validatePuzzle(grid);
    const [, options] = fetchSpy.mock.calls[0];
    const body = JSON.parse(options.body);
    expect(body.currentGrid).toEqual({ rows: grid });
  });

  it('getHint POSTs to the correct endpoint', async () => {
    fetchSpy.mockResolvedValue(new Response(JSON.stringify(CANNED_HINT), { status: 200 }));
    const { getHint } = await import('./sudokuApi.js');
    await getHint([]);
    expect(fetchSpy).toHaveBeenCalledWith(
      'http://test-api/v1/puzzles/hint',
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('getHint returns null on 404 (NoStrategyApplied) instead of throwing', async () => {
    // @spec HE-UI-010 — 404 from /puzzles/hint means all eligible strategies exhausted;
    // callers use null to trigger the fallback retry with a clean exclusion list.
    fetchSpy.mockResolvedValue(new Response(null, { status: 404 }));
    const { getHint } = await import('./sudokuApi.js');
    const result = await getHint(Array(9).fill(Array(9).fill(0)), null, [20, 40]);
    expect(result).toBeNull();
  });

  it('getHint still throws on other non-OK statuses', async () => {
    fetchSpy.mockResolvedValue(new Response('Server Error', { status: 500 }));
    const { getHint } = await import('./sudokuApi.js');
    await expect(getHint([])).rejects.toThrow('HTTP 500');
  });

  it('getCandidates POSTs to the correct endpoint', async () => {
    fetchSpy.mockResolvedValue(new Response(JSON.stringify({ candidatesGrid: { rows: [] } }), { status: 200 }));
    const { getCandidates } = await import('./sudokuApi.js');
    await getCandidates([]);
    expect(fetchSpy).toHaveBeenCalledWith(
      'http://test-api/v1/puzzles/candidates',
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('getCandidates unwraps candidatesGrid from wire format', async () => {
    const wireRows = [[[1, 2]], [[3]]];
    fetchSpy.mockResolvedValue(new Response(JSON.stringify({ candidatesGrid: { rows: wireRows } }), { status: 200 }));
    const { getCandidates } = await import('./sudokuApi.js');
    const result = await getCandidates([]);
    expect(result.candidatesGrid).toEqual(wireRows);
  });

  it('saveGame wraps currentGrid and candidates in wire format', async () => {
    fetchSpy.mockResolvedValue(new Response(null, { status: 204 }));
    const { saveGame } = await import('./sudokuApi.js');
    const grid = Array(9)
      .fill(null)
      .map(() => Array(9).fill(0));
    const candidates = Array(9)
      .fill(null)
      .map(() =>
        Array(9)
          .fill(null)
          .map(() => [])
      );
    await saveGame('gid', { currentGrid: grid, candidates, timeSpentSeconds: 0 });
    const [, options] = fetchSpy.mock.calls[0];
    const body = JSON.parse(options.body);
    expect(body.currentGrid).toEqual({ rows: grid });
    expect(body.candidates).toEqual({ rows: candidates });
  });

  it('throws when the server returns a non-OK status', async () => {
    fetchSpy.mockResolvedValue(new Response('Not Found', { status: 404 }));
    const { generatePuzzle } = await import('./sudokuApi.js');
    await expect(generatePuzzle('easy')).rejects.toThrow('HTTP 404');
  });

  it('uses errorBody.message as the error text when present', async () => {
    fetchSpy.mockResolvedValue(
      new Response(JSON.stringify({ code: 'GAME_NOT_FOUND', message: 'Game not found' }), {
        status: 404,
        headers: { 'content-type': 'application/json' },
      })
    );
    const { loadGame } = await import('./sudokuApi.js');
    await expect(loadGame('bad-id')).rejects.toThrow('Game not found');
  });

  it('falls back to errorBody.error when message is absent', async () => {
    fetchSpy.mockResolvedValue(
      new Response(JSON.stringify({ error: 'legacy error' }), {
        status: 500,
        headers: { 'content-type': 'application/json' },
      })
    );
    const { generatePuzzle } = await import('./sudokuApi.js');
    await expect(generatePuzzle('easy')).rejects.toThrow('legacy error');
  });

  it('throws ForbiddenError when the server returns 403', async () => {
    fetchSpy.mockResolvedValue(new Response('Forbidden', { status: 403 }));
    const { generatePuzzle, ForbiddenError } = await import('./sudokuApi.js');
    await expect(generatePuzzle('easy')).rejects.toBeInstanceOf(ForbiddenError);
  });

  it('returns null for 204 No Content responses', async () => {
    fetchSpy.mockResolvedValue(new Response(null, { status: 204 }));
    const { saveGame } = await import('./sudokuApi.js');
    const result = await saveGame('gid', {
      currentGrid: [],
      candidates: [],
      timeSpentSeconds: 0,
    });
    expect(result).toBeNull();
  });

  it('includes Content-Type application/json on POST requests', async () => {
    fetchSpy.mockResolvedValue(new Response(JSON.stringify(CANNED_VALIDATE_VALID), { status: 200 }));
    const { validatePuzzle } = await import('./sudokuApi.js');
    await validatePuzzle([]);
    const [, options] = fetchSpy.mock.calls[0];
    expect(options.headers).toMatchObject({ 'Content-Type': 'application/json' });
  });

  // @spec GH-UI-001
  it('getGameHistory GETs the correct endpoint and returns entries', async () => {
    fetchSpy.mockResolvedValue(
      new Response(
        JSON.stringify({
          entries: [
            {
              gameId: 'g1',
              difficulty: 'easy',
              outcome: 'won',
              endedAt: '2026-05-24T10:00:00Z',
              elapsedSeconds: 300,
              hintsUsed: 0,
            },
          ],
        }),
        { status: 200 }
      )
    );
    const { getGameHistory } = await import('./sudokuApi.js');
    const result = await getGameHistory(10);
    expect(fetchSpy).toHaveBeenCalledWith('http://test-api/v1/games/history?limit=10', expect.any(Object));
    expect(result).toHaveLength(1);
    expect(result[0].gameId).toBe('g1');
  });

  // @spec GH-UI-001
  it('getGameHistory returns empty array when entries is missing', async () => {
    fetchSpy.mockResolvedValue(new Response(JSON.stringify({}), { status: 200 }));
    const { getGameHistory } = await import('./sudokuApi.js');
    const result = await getGameHistory();
    expect(result).toEqual([]);
  });

  it('importPuzzle returns originalGrid as a plain 2D array (not unwrapped via gridFromWire)', async () => {
    // The image recognition lambda returns originalGrid as a raw 2D array, not {rows:[...]}.
    // If gridFromWire were applied, originalGrid would become undefined (array has no .rows),
    // causing createGameFromGrid to send {originalGrid:null} and the backend to reject with
    // "Grid must be 9 rows".
    const rawGrid = Array(9)
      .fill(null)
      .map(() => Array(9).fill(0));
    fetchSpy.mockResolvedValue(
      new Response(JSON.stringify({ originalGrid: rawGrid, validPuzzle: true }), { status: 200 })
    );
    const { importPuzzle } = await import('./sudokuApi.js');
    const file = new File(['fake'], 'puzzle.png', { type: 'image/png' });
    const result = await importPuzzle(file);
    expect(result.originalGrid).toEqual(rawGrid);
    expect(Array.isArray(result.originalGrid)).toBe(true);
    expect(Array.isArray(result.originalGrid[0])).toBe(true);
  });

  // @spec LT-UI-001
  it('getLeaderboard GETs the correct endpoint and returns entries', async () => {
    const mockEntries = [
      {
        userId: 'u1',
        displayName: 'Ed',
        avatarKey: 'Person',
        rank: 1,
        totalWins: 5,
        totalGames: 6,
        avgScore: 180,
        avgElapsedSeconds: 250,
        bestTimeByDifficulty: { easy: 90 },
      },
    ];
    fetchSpy.mockResolvedValue(new Response(JSON.stringify({ entries: mockEntries }), { status: 200 }));
    const { getLeaderboard } = await import('./sudokuApi.js');
    const result = await getLeaderboard();
    expect(fetchSpy).toHaveBeenCalledWith('http://test-api/v1/leaderboard', expect.any(Object));
    expect(result.entries).toHaveLength(1);
    expect(result.entries[0].displayName).toBe('Ed');
  });

  it('getLeaderboard sends Authorization header', async () => {
    fetchSpy.mockResolvedValue(new Response(JSON.stringify({ entries: [] }), { status: 200 }));
    const { getLeaderboard } = await import('./sudokuApi.js');
    await getLeaderboard();
    const [, options] = fetchSpy.mock.calls[0];
    expect(options.headers).toMatchObject({ Authorization: 'Bearer test-id-token' });
  });

  // @spec UM-BE-060, UM-BE-061
  it('getAdminData GETs /admin/data/<entity> and sends the Bearer token', async () => {
    fetchSpy.mockResolvedValue(new Response(JSON.stringify({ items: [] }), { status: 200 }));
    const { getAdminData } = await import('./sudokuApi.js');
    await getAdminData('players');
    expect(fetchSpy).toHaveBeenCalledWith('http://test-api/v1/admin/data/players', expect.any(Object));
    const [, options] = fetchSpy.mock.calls[0];
    expect(options.headers).toMatchObject({ Authorization: 'Bearer test-id-token' });
  });

  it('getAdminData throws ForbiddenError on 403 (non-admin)', async () => {
    fetchSpy.mockResolvedValue(new Response('Forbidden', { status: 403 }));
    const { getAdminData, ForbiddenError } = await import('./sudokuApi.js');
    await expect(getAdminData('games')).rejects.toBeInstanceOf(ForbiddenError);
  });
});

// ─── isAdmin / getAdminGroups ──────────────────────────────────────────────────

describe('sudokuApi — isAdmin / getAdminGroups', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubEnv('VITE_MOCK_API', 'false');
    vi.stubEnv('VITE_API_URL', 'http://test-api/v1');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.doUnmock('aws-amplify/auth');
  });

  function mockSession(groups) {
    vi.doMock('aws-amplify/auth', () => ({
      fetchAuthSession: vi.fn().mockResolvedValue({
        tokens: { idToken: { toString: () => 'test-id-token', payload: { 'cognito:groups': groups } } },
      }),
    }));
  }

  it('isAdmin returns true when cognito:groups contains administrators', async () => {
    mockSession(['administrators', 'other-group']);
    const { isAdmin } = await import('./sudokuApi.js');
    expect(await isAdmin()).toBe(true);
  });

  it('isAdmin returns false when cognito:groups does not contain administrators', async () => {
    mockSession(['other-group']);
    const { isAdmin } = await import('./sudokuApi.js');
    expect(await isAdmin()).toBe(false);
  });

  it('getAdminGroups returns an empty array when cognito:groups claim is missing', async () => {
    mockSession(undefined);
    const { getAdminGroups, isAdmin } = await import('./sudokuApi.js');
    expect(await getAdminGroups()).toEqual([]);
    expect(await isAdmin()).toBe(false);
  });
});

// ─── Mock mode — leaderboard ──────────────────────────────────────────────────

describe('sudokuApi — mock mode getLeaderboard (VITE_MOCK_API=true)', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubEnv('VITE_MOCK_API', 'true');
    vi.stubEnv('VITE_API_URL', 'http://test-api/v1');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  // @spec LT-UI-002
  it('getLeaderboard returns CANNED_LEADERBOARD without calling fetch', async () => {
    const { getLeaderboard } = await import('./sudokuApi.js');
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const result = await getLeaderboard();
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(result).toHaveProperty('entries');
    expect(Array.isArray(result.entries)).toBe(true);
    fetchSpy.mockRestore();
  });
});
