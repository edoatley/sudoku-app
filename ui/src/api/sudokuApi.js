import { CANNED_PUZZLES, CANNED_VALIDATE_VALID, CANNED_HINT } from '../mocks/cannedData.js';

const API_URL = import.meta.env.VITE_API_URL;
const MOCK_API = import.meta.env.VITE_MOCK_API === 'true';

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function generatePuzzle(difficulty) {
  if (MOCK_API) {
    await delay(400);
    return CANNED_PUZZLES[difficulty] ?? CANNED_PUZZLES.easy;
  }

  const res = await fetch(`${API_URL}/puzzles/generate?difficulty=${difficulty}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function validatePuzzle(currentGrid) {
  if (MOCK_API) {
    await delay(400);
    return CANNED_VALIDATE_VALID;
  }

  const res = await fetch(`${API_URL}/puzzles/validate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ grid: currentGrid }),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function getHint(currentGrid) {
  if (MOCK_API) {
    await delay(400);
    return CANNED_HINT;
  }

  const res = await fetch(`${API_URL}/puzzles/hint`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ grid: currentGrid }),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
