/**
 * Reads a game's persisted state directly from DynamoDB Local (the same table the
 * docker-compose.test.yml `dynamodb-setup` service creates) and checks Sudoku board
 * validity, so scenarios can assert on the DynamoDB row rather than just the UI.
 *
 * Uses the AWS CLI rather than adding an @aws-sdk/client-dynamodb devDependency for one
 * read — consistent with how scripts/logs/*.sh already shell out to `aws` for this repo's
 * other local/CloudWatch tooling.
 */
import { execFileSync } from 'node:child_process';

const ENDPOINT_URL = process.env.COACH_QUALITY_DYNAMODB_ENDPOINT ?? 'http://localhost:8000';
const TABLE_NAME = 'SudokuGames';
// Fixed mock principal injected by DevUserFilter (backend/.../developer/DevUserFilter.java)
// for the dev/it/test build profiles when no Authorization header is present — matches
// what the browser's unauthenticated requests resolve to under VITE_SKIP_AUTH=true.
const USER_ID = 'local-dev-user';

// DynamoDB Local isolates data per region+credentials combination. Must match exactly what
// dynamodb-setup used to create the tables (docker-compose.test.yml: us-east-1 / test / test).
// AWS_REGION must be cleared too, not just AWS_DEFAULT_REGION — the AWS CLI prefers AWS_REGION,
// and the coach-quality wrapper script exports AWS_REGION=eu-west-2 for Bedrock, which would
// otherwise leak through process.env and point this at a different (empty) region bucket.
//
// AWS_PROFILE and AWS_SESSION_TOKEN (from the sandbox SSO session, also inherited via
// process.env) must be deleted rather than set to '' — the AWS CLI treats an empty-string
// AWS_PROFILE as a real (nonexistent) profile name and fails ("config profile () could not
// be found") instead of falling back to the explicit access key below.
const AWS_CLI_ENV = {
  ...process.env,
  AWS_ACCESS_KEY_ID: 'test',
  AWS_SECRET_ACCESS_KEY: 'test',
  AWS_REGION: 'us-east-1',
  AWS_DEFAULT_REGION: 'us-east-1',
};
delete AWS_CLI_ENV.AWS_PROFILE;
delete AWS_CLI_ENV.AWS_SESSION_TOKEN;

/**
 * @param {string} gameId
 * @returns {Promise<{currentGrid: number[][], solutionGrid: number[][], status: string, hintsUsed: number}>}
 */
export async function getGameItem(gameId) {
  const key = JSON.stringify({ userId: { S: USER_ID }, gameId: { S: gameId } });
  const raw = execFileSync(
    'aws',
    ['dynamodb', 'get-item', '--endpoint-url', ENDPOINT_URL, '--table-name', TABLE_NAME, '--key', key],
    { encoding: 'utf8', env: AWS_CLI_ENV }
  );
  const { Item } = JSON.parse(raw);
  if (!Item) {
    throw new Error(`getGameItem: no SudokuGames row found for userId=${USER_ID} gameId=${gameId}`);
  }
  // Grids are stored as JSON-string attributes, not nested DynamoDB documents
  // (see backend/.../game/persistence/GameItem.java).
  return {
    currentGrid: JSON.parse(Item.currentGrid.S),
    solutionGrid: JSON.parse(Item.solutionGrid.S),
    status: Item.status.S,
    hintsUsed: Number(Item.hintsUsed?.N ?? 0),
  };
}

/** True if no row, column, or 3x3 box has a repeated non-zero digit. */
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
