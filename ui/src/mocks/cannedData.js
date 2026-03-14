export const CANNED_PUZZLES = {
  easy: {
    difficulty: 'easy',
    grid: [
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
  },
  medium: {
    difficulty: 'medium',
    grid: [
      [0, 0, 0, 2, 6, 0, 7, 0, 1],
      [6, 8, 0, 0, 7, 0, 0, 9, 0],
      [1, 9, 0, 0, 0, 4, 5, 0, 0],
      [8, 2, 0, 1, 0, 0, 0, 4, 0],
      [0, 0, 4, 6, 0, 2, 9, 0, 0],
      [0, 5, 0, 0, 0, 3, 0, 2, 8],
      [0, 0, 9, 3, 0, 0, 0, 7, 4],
      [0, 4, 0, 0, 5, 0, 0, 3, 6],
      [7, 0, 3, 0, 1, 8, 0, 0, 0],
    ],
  },
  hard: {
    difficulty: 'hard',
    grid: [
      [0, 0, 0, 0, 0, 0, 6, 8, 0],
      [0, 0, 0, 0, 7, 3, 0, 0, 9],
      [3, 0, 9, 0, 0, 0, 0, 4, 5],
      [4, 9, 0, 0, 0, 0, 0, 0, 0],
      [8, 0, 3, 0, 5, 0, 9, 0, 2],
      [0, 0, 0, 0, 0, 0, 0, 3, 6],
      [9, 6, 0, 0, 0, 0, 3, 0, 8],
      [7, 0, 0, 6, 8, 0, 0, 0, 0],
      [0, 2, 8, 0, 0, 0, 0, 0, 0],
    ],
  },
};

export const CANNED_VALIDATE_VALID = {
  valid: true,
  errors: [],
  message: 'Board is valid so far.',
};

export const CANNED_VALIDATE_INVALID = {
  valid: false,
  errors: [{ row: 0, col: 4 }, { row: 1, col: 3 }],
  message: 'The board contains errors.',
};

export const CANNED_HINT = {
  coordinate: { row: 0, col: 2 },
  value: 4,
};
