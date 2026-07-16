/**
 * Writes a scenario run's full trace to disk as evidence for manual analysis — this is the
 * primary deliverable of the suite, not a side effect. Written unconditionally (pass or fail):
 * the whole point is being able to dig into what the real system did, regardless of whether
 * every `assert` action happened to match.
 *
 * Two files per run, in `reports/` (gitignored):
 *   <scenario>-<timestamp>.json — the complete trace: every action, full request/response
 *     payloads, correlated structured log lines, timings, assertion outcomes.
 *   <scenario>-<timestamp>.md   — a human-readable transcript in the spirit of the manual
 *     walkthroughs in docs/tests/ai-coach.md (conversation, grid, hint detail, evidence log).
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const REPORTS_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../reports');

function gridToLines(grid) {
  return grid.map((row) => row.map((v) => (v === 0 ? '_' : v)).join(' ')).join('\n');
}

function renderStep(step) {
  const { action, result } = step;
  switch (action.type) {
    case 'move':
      return (
        `- **Move** place \`${action.v}\` at (r${action.r}, c${action.c})` +
        (result.skipped ? ' — **skipped** (given/clue cell, no-op)' : ` — was \`${result.prevValue || '_'}\``)
      );
    case 'clear':
      return (
        `- **Clear** (r${action.r}, c${action.c})` +
        (result.skipped ? ' — **skipped** (given/clue cell, no-op)' : ` — removed \`${result.prevValue}\``)
      );
    case 'undo':
      return result.skipped
        ? `- **Undo** — **skipped** (nothing to undo)`
        : `- **Undo** — restored (r${result.r}, c${result.c}) from \`${result.removed}\` to \`${result.restored || '_'}\``;
    case 'hint':
      if (result.status === 'solved') return `- **Hint** — puzzle already solved`;
      if (result.status === 'no-strategy') return `- **Hint** — no strategy found at the requested rank`;
      return (
        `- **Hint** — **${result.hint.techniqueName}** (rank ${result.hint.strategyRank}, ${result.hint.difficulty})\n` +
        `  - nudge: ${result.hint.nudge}`
      );
    case 'ask': {
      const fb = result.logPair?.response?.fallback;
      const fbNote = result.logPair?.skipped
        ? `(${result.logPair.skipped})`
        : result.logPair?.error
          ? `(${result.logPair.error})`
          : fb === undefined
            ? '(no log pair matched)'
            : fb
              ? '**FALLBACK**'
              : 'real reply';
      return (
        `- **User:** ${action.text}\n` +
        `  - **AI** (${fbNote}, revealHint=${result.apiResponse?.revealHint ?? 'n/a'}, responseType=${result.logPair?.response?.responseType ?? 'n/a'}): ${result.apiResponse?.aiMessage ?? '(204 — puzzle solved)'}`
      );
    }
    case 'sync':
      return `- **Sync** — flushed ${result.eventsFlushed} buffered event(s); status=${result.gameState?.status}, hintsUsed=${result.gameState?.hintsUsed}`;
    case 'assert':
      return `- ${result.pass ? '✅' : '❌'} **assert ${action.kind}** — expected \`${JSON.stringify(result.expected)}\`, got \`${JSON.stringify(result.actual)}\``;
    default:
      return `- ${action.type}`;
  }
}

function renderMarkdown(trace) {
  const lines = [];
  lines.push(`# Coach quality report — ${trace.scenario}`);
  lines.push('');
  lines.push(`- gameId: \`${trace.gameId}\``);
  lines.push(`- started: ${trace.startedAt}`);
  lines.push(`- finished: ${trace.finishedAt}`);
  lines.push(
    `- assertions: ${trace.assertionSummary.passed}/${trace.assertionSummary.total} passed` +
      (trace.assertionSummary.failed > 0 ? ` — **${trace.assertionSummary.failed} FAILED**` : '')
  );
  lines.push('');
  lines.push('## Initial grid');
  lines.push('```');
  lines.push(gridToLines(trace.initialGrid));
  lines.push('```');
  lines.push('');
  lines.push('## Steps');
  for (const step of trace.steps) {
    lines.push(renderStep(step));
  }
  lines.push('');
  if (trace.finalGameState) {
    lines.push('## Final board (from GET /games/{id})');
    lines.push('```');
    lines.push(gridToLines(trace.finalGameState.currentGrid.rows));
    lines.push('```');
    lines.push('');
  } else if (trace.finalGameStateError) {
    lines.push('## Final board (from GET /games/{id})');
    lines.push(`_Could not fetch final game state: ${trace.finalGameStateError}_`);
    lines.push('');
  }
  lines.push('## Evidence — full structured log stream for this game (pid)');
  lines.push('```json');
  for (const line of trace.finalLogs) {
    lines.push(JSON.stringify(line));
  }
  lines.push('```');
  return lines.join('\n');
}

/**
 * @param {object} trace see lib/runner.js for the shape this is built from
 * @returns {{jsonPath: string, mdPath: string}}
 */
export function writeReport(trace) {
  fs.mkdirSync(REPORTS_DIR, { recursive: true });
  const stamp = trace.startedAt.replace(/[:.]/g, '-');
  const base = `${trace.scenario}-${stamp}`;
  const jsonPath = path.join(REPORTS_DIR, `${base}.json`);
  const mdPath = path.join(REPORTS_DIR, `${base}.md`);

  fs.writeFileSync(jsonPath, JSON.stringify(trace, null, 2));
  fs.writeFileSync(mdPath, renderMarkdown(trace));

  return { jsonPath, mdPath };
}
