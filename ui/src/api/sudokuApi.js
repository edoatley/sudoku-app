// @spec DT-UI-005, DT-UI-006, DT-UI-007, DT-UI-008
import { CANNED_PUZZLES, CANNED_VALIDATE_VALID, CANNED_HINT, CANNED_CANDIDATES, CANNED_GAME_STATE } from '../mocks/cannedData.js';
import { gridFromWire, gridToWire, candidatesFromWire, candidatesToWire } from '../utils/gridAdapters.js';

const API_URL = import.meta.env.VITE_API_URL;
const MOCK_API = import.meta.env.VITE_MOCK_API === 'true';
const SKIP_AUTH = import.meta.env.VITE_SKIP_AUTH === 'true';
const LOG_API = import.meta.env.VITE_LOG_API === 'true';

export class ForbiddenError extends Error {
  constructor() {
    super('Access denied');
    this.name = 'ForbiddenError';
  }
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function getIdToken() {
  const { fetchAuthSession } = await import('aws-amplify/auth');
  const session = await fetchAuthSession();
  return session.tokens?.idToken?.toString();
}

export async function getEmailFromSession() {
  const { fetchAuthSession } = await import('aws-amplify/auth');
  const session = await fetchAuthSession();
  return session.tokens?.idToken?.payload?.email ?? null;
}

async function apiFetch(label, url, options = {}, authenticated = false) {
  if (LOG_API) {
    const body = options.body ? JSON.parse(options.body) : undefined;
    console.group(`[API] ${options.method ?? 'GET'} ${label}`);
    console.log('→ request:', { url, ...(body !== undefined && { body }) });
  }

  const headers = { ...options.headers };
  if (authenticated && !SKIP_AUTH) {
    const token = await getIdToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(url, { ...options, headers });
  if (!res.ok) {
    if (LOG_API) {
      console.log('← error:', res.status);
      console.groupEnd();
    }
    if (res.status === 403) throw new ForbiddenError();
    let message = `HTTP ${res.status}`;
    try {
      const contentType = res.headers.get('content-type') ?? '';
      if (contentType.includes('application/json')) {
        const errorBody = await res.json();
        if (typeof errorBody.message === 'string' && errorBody.message.length > 0) {
          message = errorBody.message;
        } else if (typeof errorBody.error === 'string' && errorBody.error.length > 0) {
          message = errorBody.error;
        }
      }
    } catch {
      // Body could not be parsed — fall back to HTTP status message
    }
    throw new Error(message);
  }
  if (res.status === 204 || res.headers.get('content-length') === '0') {
    if (LOG_API) { console.log('← (no content)'); console.groupEnd(); }
    return null;
  }
  const data = await res.json();
  if (LOG_API) {
    console.log('← response:', data);
    console.groupEnd();
  }
  return data;
}

// @spec DT-UI-006 — unwrap grid fields from a PuzzleResponse wire object
function unwrapPuzzleResponse(data) {
  if (!data) return data;
  return {
    ...data,
    originalGrid: gridFromWire(data.originalGrid),
    solutionGrid: gridFromWire(data.solutionGrid),
  };
}

// @spec DT-UI-006 — unwrap grid fields from a GameState wire object
function unwrapGameState(data) {
  if (!data) return data;
  return {
    ...data,
    originalGrid: gridFromWire(data.originalGrid),
    currentGrid: gridFromWire(data.currentGrid),
    solutionGrid: gridFromWire(data.solutionGrid),
    candidates: candidatesFromWire(data.candidates),
  };
}

export async function generatePuzzle(difficulty, signal) {
  if (MOCK_API) {
    await delay(400);
    return unwrapPuzzleResponse(CANNED_PUZZLES[difficulty] ?? CANNED_PUZZLES.easy);
  }

  const data = await apiFetch('generatePuzzle', `${API_URL}/puzzles/generate?difficulty=${difficulty}`, { signal });
  return unwrapPuzzleResponse(data);
}

export async function validatePuzzle(currentGrid, solutionGrid = null) {
  if (MOCK_API) {
    await delay(400);
    return CANNED_VALIDATE_VALID;
  }

  return apiFetch('validatePuzzle', `${API_URL}/puzzles/validate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ currentGrid: gridToWire(currentGrid), solutionGrid: gridToWire(solutionGrid) }),
  });
}

export async function getHint(currentGrid, minRank = null, excludedRanks = null) {
  if (MOCK_API) {
    await delay(400);
    return CANNED_HINT;
  }

  const body = { currentGrid: gridToWire(currentGrid) };
  if (minRank != null) body.minRank = minRank;
  if (excludedRanks?.length > 0) body.excludedRanks = excludedRanks;
  return apiFetch('getHint', `${API_URL}/puzzles/hint`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

export async function getCandidates(currentGrid) {
  if (MOCK_API) {
    await delay(400);
    return CANNED_CANDIDATES;
  }

  const data = await apiFetch('getCandidates', `${API_URL}/puzzles/candidates`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ currentGrid: gridToWire(currentGrid) }),
  });
  return data ? { ...data, candidatesGrid: candidatesFromWire(data.candidatesGrid) } : data;
}

export async function createGame(difficulty) {
  if (MOCK_API) {
    await delay(400);
    return unwrapGameState({ ...CANNED_GAME_STATE, difficulty });
  }

  const data = await apiFetch('createGame', `${API_URL}/games`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ difficulty }),
  }, true);
  return unwrapGameState(data);
}

export async function loadGame(gameId) {
  if (MOCK_API) {
    await delay(200);
    return unwrapGameState({ ...CANNED_GAME_STATE, gameId });
  }

  const data = await apiFetch('loadGame', `${API_URL}/games/${gameId}`, {}, true);
  return unwrapGameState(data);
}

export async function getCurrentGame() {
  if (MOCK_API) {
    await delay(200);
    return null;
  }

  const data = await apiFetch('getCurrentGame', `${API_URL}/games/current`, {}, true);
  return unwrapGameState(data);
}

export async function saveGame(gameId, { currentGrid, candidates, timeSpentSeconds, isComplete = false, hintsUsed }) {
  if (MOCK_API) {
    await delay(100);
    return;
  }

  return apiFetch('saveGame', `${API_URL}/games/${gameId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      currentGrid: gridToWire(currentGrid),
      candidates: candidatesToWire(candidates),
      timeSpentSeconds,
      isComplete,
      hintsUsed,
    }),
  }, true);
}

function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result.split(',')[1]);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

export async function importPuzzle(imageFile) {
  if (MOCK_API) {
    await delay(1200);
    return { originalGrid: gridFromWire(CANNED_PUZZLES.easy.originalGrid) };
  }

  const base64 = await fileToBase64(imageFile);
  const data = await apiFetch('importPuzzle', `${API_URL}/puzzles/import`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ image: base64 }),
  }, true);
  return data ? { ...data, originalGrid: gridFromWire(data.originalGrid) } : data;
}

export async function warmupImageRecognition() {
  if (MOCK_API) return;
  try {
    await fetch(`${API_URL}/puzzles/import/warmup`);
  } catch {
    // silent — warm-up is best-effort
  }
}

export async function getDemoGrid(technique) {
  if (MOCK_API) {
    await delay(200);
    return unwrapPuzzleResponse(CANNED_PUZZLES.easy);
  }

  const data = await apiFetch('getDemoGrid', `${API_URL}/dev/hint-demo?technique=${encodeURIComponent(technique)}`);
  return unwrapPuzzleResponse(data);
}

export async function getPlayerProfile() {
  if (MOCK_API) return null;
  return apiFetch('getPlayerProfile', `${API_URL}/players/me`, {}, true);
}

// @spec UM-API-002, UM-UI-008
export async function updatePlayerProfile({ displayName, avatarKey }) {
  if (MOCK_API) {
    await delay(200);
    return { userId: 'mock-user', email: '', displayName, avatarKey, createdAt: '', updatedAt: '' };
  }
  return apiFetch('updatePlayerProfile', `${API_URL}/players/me`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ displayName, avatarKey }),
  }, true);
}

export async function getDevData(entity) {
  if (MOCK_API) {
    await delay(200);
    return { items: [] };
  }
  return apiFetch(`getDevData/${entity}`, `${API_URL}/dev/data/${encodeURIComponent(entity)}`);
}

export async function createGameFromGrid(originalGrid) {
  if (MOCK_API) {
    await delay(300);
    const base = unwrapGameState(CANNED_GAME_STATE);
    return { ...base, originalGrid, currentGrid: originalGrid.map((r) => [...r]) };
  }

  const data = await apiFetch('createGameFromGrid', `${API_URL}/games/from-image`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ originalGrid: gridToWire(originalGrid) }),
  }, true);
  return unwrapGameState(data);
}
