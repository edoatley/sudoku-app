// @spec DT-UI-009 — all grid fields use {rows: [...]} wire format to match the backend API

export const CANNED_PUZZLES = {
  easy: {
    difficulty: 'easy',
    originalGrid: {
      rows: [
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
  },
  medium: {
    difficulty: 'medium',
    originalGrid: {
      rows: [
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
  },
  hard: {
    difficulty: 'hard',
    originalGrid: {
      rows: [
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
  },
};

export const CANNED_VALIDATE_VALID = {
  isValid: true,
  isSolved: false,
  errors: [],
};

export const CANNED_VALIDATE_INVALID = {
  isValid: false,
  isSolved: false,
  errors: [{ row: 0, col: 4 }, { row: 1, col: 3 }],
};

export const CANNED_HINT = {
  techniqueName: "Naked Single",
  markdownSlug: "naked-single",
  difficulty: "easy",
  strategyRank: 20,
  nudge: "There is a Naked Single hiding somewhere on the board.",
  focus: "Look closely at cell (0, 2) — its row, column, and box together eliminate 8 numbers.",
  reveal: "Cell (0, 2) can only be 4. All other numbers appear in its row, column, or 3×3 block.",
  highlightCells: [{ row: 0, col: 2 }],
  eliminatedCandidates: [],
  solvedCells: [{ row: 0, col: 2, value: 4 }],
  focusCandidates: [{ row: 0, col: 2, value: 4 }],
};

const EASY_GRID_ROWS = [
  [5, 3, 0, 0, 7, 0, 0, 0, 0],
  [6, 0, 0, 1, 9, 5, 0, 0, 0],
  [0, 9, 8, 0, 0, 0, 0, 6, 0],
  [8, 0, 0, 0, 6, 0, 0, 0, 3],
  [4, 0, 0, 8, 0, 3, 0, 0, 1],
  [7, 0, 0, 0, 2, 0, 0, 0, 6],
  [0, 6, 0, 0, 0, 0, 2, 8, 0],
  [0, 0, 0, 4, 1, 9, 0, 0, 5],
  [0, 0, 0, 0, 8, 0, 0, 7, 9],
];

export const CANNED_GAME_STATE = {
  gameId: '123e4567-e89b-12d3-a456-426614174000',
  difficulty: 'easy',
  originalGrid: { rows: EASY_GRID_ROWS },
  currentGrid: { rows: EASY_GRID_ROWS },
  candidates: { rows: Array(9).fill(null).map(() => Array(9).fill(null).map(() => [])) },
  timeSpentSeconds: 0,
  status: 'IN_PROGRESS',
};

// Candidates for the easy puzzle: [5,3,0,0,7,0,0,0,0 / 6,0,0,1,9,5,0,0,0 / ...]
// Filled cells get [], empty cells get plausible candidates.
export const CANNED_CANDIDATES = {
  candidatesGrid: {
    rows: [
      // row 0: [5,3,0,0,7,0,0,0,0]
      [[], [], [1,2,4,6], [2,4,6,8], [], [2,4,6,8], [1,4,8,9], [1,2,4,9], [2,4,8]],
      // row 1: [6,0,0,1,9,5,0,0,0]
      [[], [2,4,7], [2,4,7], [], [], [], [3,4,7,8], [2,3,4], [2,3,4,7,8]],
      // row 2: [0,9,8,0,0,0,0,6,0]
      [[1,2,4], [], [], [2,3,4,6], [3,4,6], [2,4,6], [1,4,7], [], [2,4,7]],
      // row 3: [8,0,0,0,6,0,0,0,3]
      [[], [1,2,4,5,7], [1,2,5,7], [5,7,9], [], [1,4,7,9], [4,5,7,9], [1,2,4,5,9], []],
      // row 4: [4,0,0,8,0,3,0,0,1]
      [[], [2,5,6,7], [2,5,6,7], [], [5,6,9], [], [5,6,7,9], [2,5,9], []],
      // row 5: [7,0,0,0,2,0,0,0,6]
      [[], [1,3,5], [1,3,5], [5,9], [], [1,4,9], [4,5,8,9], [1,3,4,5,9], []],
      // row 6: [0,6,0,0,0,0,2,8,0]
      [[1,3,5,9], [], [1,3,5,7], [5,7,9], [3,4,5,7], [1,4,7], [], [], [4,7]],
      // row 7: [0,0,0,4,1,9,0,0,5]
      [[2,3], [2,3,8], [2,3,7], [], [], [], [3,6,7,8], [2,3], []],
      // row 8: [0,0,0,0,8,0,0,7,9]
      [[1,2,3], [1,3,4], [1,3,4,6], [3,5,6], [], [1,4,6], [1,3,4,6], [], []],
    ],
  },
};

export const CANNED_LEADERBOARD = {
  entries: [
    {
      userId: 'edoatley-id',
      displayName: 'Ed',
      avatarKey: 'SportsBasketball',
      rank: 1,
      totalWins: 12,
      totalGames: 15,
      avgScore: 187,
      avgElapsedSeconds: 230,
      bestTimeByDifficulty: { easy: 95, medium: 230, hard: 720 },
    },
    {
      userId: 'hanoatley-id',
      displayName: 'Hannah',
      avatarKey: 'DirectionsRun',
      rank: 2,
      totalWins: 8,
      totalGames: 10,
      avgScore: 154,
      avgElapsedSeconds: 310,
      bestTimeByDifficulty: { easy: 110, medium: 310 },
    },
    {
      userId: 'edoatley2-id',
      displayName: 'Ed2',
      avatarKey: 'Person',
      rank: 3,
      totalWins: 5,
      totalGames: 7,
      avgScore: 120,
      avgElapsedSeconds: 420,
      bestTimeByDifficulty: { medium: 420 },
    },
    {
      userId: 'icloud-id',
      displayName: 'Ed (iCloud)',
      avatarKey: 'Person',
      rank: 4,
      totalWins: 2,
      totalGames: 4,
      avgScore: 85,
      avgElapsedSeconds: 540,
      bestTimeByDifficulty: { easy: 200 },
    },
  ],
};
