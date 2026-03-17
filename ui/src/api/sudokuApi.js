import { CANNED_PUZZLES, CANNED_VALIDATE_VALID, CANNED_HINT, CANNED_CANDIDATES } from '../mocks/cannedData.js';

const API_URL = import.meta.env.VITE_API_URL;
const MOCK_API = import.meta.env.VITE_MOCK_API === 'true';
const LOG_API = import.meta.env.VITE_LOG_API === 'true';

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function apiFetch(label, url, options = {}) {
  if (LOG_API) {
    const body = options.body ? JSON.parse(options.body) : undefined;
    console.group(`[API] ${options.method ?? 'GET'} ${label}`);
    console.log('→ request:', { url, ...(body !== undefined && { body }) });
  }
  const res = await fetch(url, options);
  if (!res.ok) {
    if (LOG_API) {
      console.log('← error:', res.status);
      console.groupEnd();
    }
    throw new Error(`HTTP ${res.status}`);
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

export async function validatePuzzle(currentGrid) {
  if (MOCK_API) {
    await delay(400);
    return CANNED_VALIDATE_VALID;
  }

  return apiFetch('validatePuzzle', `${API_URL}/puzzles/validate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ currentGrid }),
  });
}

export async function getHint(currentGrid) {
  if (MOCK_API) {
    await delay(400);
    return CANNED_HINT;
  }

  return apiFetch('getHint', `${API_URL}/puzzles/hint`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ currentGrid }),
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
