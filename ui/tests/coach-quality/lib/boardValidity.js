// @spec CQ-DAT-001
/**
 * True if no row, column, or 3x3 box has a repeated non-zero digit — used to assert board
 * validity from the grid returned by the backend REST API (GET /games/{id}), not a direct
 * DynamoDB read.
 */
export function isBoardValid(grid) {
  const noDuplicates = (cells) => {
    const filled = cells.filter((v) => v !== 0);
    return new Set(filled).size === filled.length;
  };

  for (let i = 0; i < 9; i++) {
    if (!noDuplicates(grid[i])) return false;
    if (!noDuplicates(grid.map((row) => row[i]))) return false;
  }
  for (let boxRow = 0; boxRow < 9; boxRow += 3) {
    for (let boxCol = 0; boxCol < 9; boxCol += 3) {
      const box = [];
      for (let r = boxRow; r < boxRow + 3; r++) {
        for (let c = boxCol; c < boxCol + 3; c++) {
          box.push(grid[r][c]);
        }
      }
      if (!noDuplicates(box)) return false;
    }
  }
  return true;
}
