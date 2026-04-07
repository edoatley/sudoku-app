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

  it('generatePuzzle returns canned easy puzzle without calling fetch', async () => {
    const { generatePuzzle } = await import('./sudokuApi.js');
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const result = await generatePuzzle('easy');
    expect(result).toEqual(CANNED_PUZZLES.easy);
    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });

  it('generatePuzzle returns easy canned data for unknown difficulty', async () => {
    const { generatePuzzle } = await import('./sudokuApi.js');
    const result = await generatePuzzle('unknown');
    expect(result).toEqual(CANNED_PUZZLES.easy);
  });

  it('generatePuzzle returns correct canned data for medium difficulty', async () => {
    const { generatePuzzle } = await import('./sudokuApi.js');
    const result = await generatePuzzle('medium');
    expect(result).toEqual(CANNED_PUZZLES.medium);
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

  it('createGame returns canned game state with the requested difficulty', async () => {
    const { createGame } = await import('./sudokuApi.js');
    const result = await createGame('hard');
    expect(result.difficulty).toBe('hard');
    expect(result.gameId).toBe(CANNED_GAME_STATE.gameId);
  });

  it('loadGame returns canned game state with the requested gameId', async () => {
    const { loadGame } = await import('./sudokuApi.js');
    const result = await loadGame('abc-123');
    expect(result.gameId).toBe('abc-123');
  });

  it('saveGame resolves without calling fetch', async () => {
    const { saveGame } = await import('./sudokuApi.js');
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    await expect(
      saveGame('abc-123', { currentGrid: [], candidates: [], timeSpentSeconds: 0 }),
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
    vi.stubEnv('VITE_API_URL', 'http://test-api/v1');
    fetchSpy = vi.spyOn(globalThis, 'fetch');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it('generatePuzzle GETs the correct endpoint', async () => {
    fetchSpy.mockResolvedValue(
      new Response(JSON.stringify({ originalGrid: [] }), { status: 200 }),
    );
    const { generatePuzzle } = await import('./sudokuApi.js');
    await generatePuzzle('medium');
    expect(fetchSpy).toHaveBeenCalledWith(
      'http://test-api/v1/puzzles/generate?difficulty=medium',
      expect.any(Object),
    );
  });

  it('validatePuzzle POSTs to the correct endpoint', async () => {
    fetchSpy.mockResolvedValue(
      new Response(JSON.stringify(CANNED_VALIDATE_VALID), { status: 200 }),
    );
    const { validatePuzzle } = await import('./sudokuApi.js');
    await validatePuzzle(Array(9).fill(Array(9).fill(0)));
    expect(fetchSpy).toHaveBeenCalledWith(
      'http://test-api/v1/puzzles/validate',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('getHint POSTs to the correct endpoint', async () => {
    fetchSpy.mockResolvedValue(
      new Response(JSON.stringify(CANNED_HINT), { status: 200 }),
    );
    const { getHint } = await import('./sudokuApi.js');
    await getHint([]);
    expect(fetchSpy).toHaveBeenCalledWith(
      'http://test-api/v1/puzzles/hint',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('getCandidates POSTs to the correct endpoint', async () => {
    fetchSpy.mockResolvedValue(
      new Response(JSON.stringify(CANNED_CANDIDATES), { status: 200 }),
    );
    const { getCandidates } = await import('./sudokuApi.js');
    await getCandidates([]);
    expect(fetchSpy).toHaveBeenCalledWith(
      'http://test-api/v1/puzzles/candidates',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('throws when the server returns a non-OK status', async () => {
    fetchSpy.mockResolvedValue(new Response('Not Found', { status: 404 }));
    const { generatePuzzle } = await import('./sudokuApi.js');
    await expect(generatePuzzle('easy')).rejects.toThrow('HTTP 404');
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
    fetchSpy.mockResolvedValue(
      new Response(JSON.stringify(CANNED_VALIDATE_VALID), { status: 200 }),
    );
    const { validatePuzzle } = await import('./sudokuApi.js');
    await validatePuzzle([]);
    const [, options] = fetchSpy.mock.calls[0];
    expect(options.headers).toMatchObject({ 'Content-Type': 'application/json' });
  });
});
