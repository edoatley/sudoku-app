import { expect } from '@playwright/test';

// Easy puzzle — row 0: [5, 3, 0, 0, 7, 0, 0, 0, 0]
// Row 0 solution:       [5, 3, 4, 6, 7, 8, 9, 1, 2]
// Given cells (not clickable): col 0 (5), col 1 (3), col 4 (7)
// Empty cells to fill:  col 2→4, col 3→6, col 5→8, col 6→9, col 7→1, col 8→2
export const EASY_PUZZLE = {
  difficulty: 'easy',
  originalGrid: [
    [5, 3, 0, 0, 7, 0, 0, 0, 0],
    [6, 0, 0, 1, 9, 5, 0, 0, 0],
    [0, 9, 8, 0, 0, 0, 0, 6, 0],
    [8, 0, 0, 0, 6, 0, 0, 0, 3],
    [4, 0, 0, 8, 0, 3, 0, 0, 1],
    [7, 0, 0, 0, 2, 0, 0, 0, 6],
    [0, 6, 0, 0, 0, 0, 2, 8, 0],
    [0, 0, 0, 4, 1, 9, 0, 0, 5],
    [0, 0, 0, 0, 8, 0, 0, 7, 9],
  ],
};

export async function setupGenerateRoute(page) {
  await page.route('**/puzzles/generate**', (route) =>
    route.fulfill({ json: EASY_PUZZLE })
  );
}

export async function waitForGrid(page) {
  await expect(page.getByTestId('cell-0-0')).toBeVisible();
}
