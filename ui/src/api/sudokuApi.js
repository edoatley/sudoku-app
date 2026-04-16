import { CANNED_PUZZLES, CANNED_VALIDATE_VALID, CANNED_HINT, CANNED_CANDIDATES, CANNED_GAME_STATE } from '../mocks/cannedData.js';

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
    throw new Error(`HTTP ${res.status}`);
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

export async function generatePuzzle(difficulty, signal) {
  if (MOCK_API) {
    await delay(400);
    return CANNED_PUZZLES[difficulty] ?? CANNED_PUZZLES.easy;
  }

  return apiFetch('generatePuzzle', `${API_URL}/puzzles/generate?difficulty=${difficulty}`, { signal });
}

export async function validatePuzzle(currentGrid, solutionGrid = null) {
  if (MOCK_API) {
    await delay(400);
    return CANNED_VALIDATE_VALID;
  }

  return apiFetch('validatePuzzle', `${API_URL}/puzzles/validate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ currentGrid, solutionGrid }),
  });
}

export async function getHint(currentGrid, minRank = null, excludedRanks = null) {
  if (MOCK_API) {
    await delay(400);
    return CANNED_HINT;
  }

  const body = { currentGrid };
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

  return apiFetch('getCandidates', `${API_URL}/puzzles/candidates`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ currentGrid }),
  });
}

export async function createGame(difficulty) {
  if (MOCK_API) {
    await delay(400);
    return { ...CANNED_GAME_STATE, difficulty };
  }

  return apiFetch('createGame', `${API_URL}/games`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ difficulty }),
  }, true);
}

export async function loadGame(gameId) {
  if (MOCK_API) {
    await delay(200);
    return { ...CANNED_GAME_STATE, gameId };
  }

  return apiFetch('loadGame', `${API_URL}/games/${gameId}`, {}, true);
}

export async function getCurrentGame() {
  if (MOCK_API) {
    await delay(200);
    return null;
  }

  return apiFetch('getCurrentGame', `${API_URL}/games/current`, {}, true);
}

export async function saveGame(gameId, { currentGrid, candidates, timeSpentSeconds, isComplete = false, hintsUsed }) {
  if (MOCK_API) {
    await delay(100);
    return;
  }

  return apiFetch('saveGame', `${API_URL}/games/${gameId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ currentGrid, candidates, timeSpentSeconds, isComplete, hintsUsed }),
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
    return { originalGrid: CANNED_PUZZLES.easy.originalGrid };
  }

  const base64 = await fileToBase64(imageFile);
  return apiFetch('importPuzzle', `${API_URL}/puzzles/import`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ image: base64 }),
  }, true);
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
    return CANNED_PUZZLES.easy;
  }

  return apiFetch('getDemoGrid', `${API_URL}/dev/hint-demo?technique=${encodeURIComponent(technique)}`);
}

export async function getPlayerProfile() {
  if (MOCK_API) return null;
  return apiFetch('getPlayerProfile', `${API_URL}/players/me`, {}, true);
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
    return { ...CANNED_GAME_STATE, originalGrid, currentGrid: originalGrid.map((r) => [...r]) };
  }

  return apiFetch('createGameFromGrid', `${API_URL}/games/from-image`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ originalGrid }),
  }, true);
}
