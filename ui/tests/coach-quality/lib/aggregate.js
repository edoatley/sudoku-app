/**
 * Folds a directory of coach-quality report JSON files (see lib/report.js) into a summary:
 * fallback rate, latency percentiles, and token totals across all COACH_RESPONSE log lines
 * found in their `finalLogs`. Report files carry no run-group label of their own — group runs
 * by moving each run's `reports/*` output into a labelled subdirectory by hand before
 * aggregating (e.g. `reports/haiku-4-5-2026-07-13/`), the same manual convention already used
 * for `reports/baseline/`.
 *
 * Usage:
 *   node tests/coach-quality/lib/aggregate.js <reports-subdir>
 *   node tests/coach-quality/lib/aggregate.js baseline
 *   node tests/coach-quality/lib/aggregate.js /absolute/path/to/reports/haiku-4-5-2026-07-13
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const REPORTS_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../reports');

function resolveTargetDir(arg) {
  if (!arg) return REPORTS_DIR;
  return path.isAbsolute(arg) ? arg : path.join(REPORTS_DIR, arg);
}

function loadCoachResponses(dir) {
  const files = fs.readdirSync(dir).filter((f) => f.endsWith('.json'));
  const responses = [];
  for (const file of files) {
    const trace = JSON.parse(fs.readFileSync(path.join(dir, file), 'utf8'));
    // Bedrock's COACH_RESPONSE log line carries its own server-measured `latencyMs`; Vertex's
    // doesn't (see VertexCoachClient) — fall back to the client-measured round-trip time from
    // the step that produced this turn, correlated by `cid`, so both providers get a latency
    // figure even though only one of them logs it server-side.
    const durationByCid = new Map();
    for (const step of trace.steps ?? []) {
      const response = step.result?.logPair?.response;
      if (response?.type === 'COACH_RESPONSE' && response.cid) {
        durationByCid.set(response.cid, step.durationMs);
      }
    }
    for (const line of trace.finalLogs ?? []) {
      if (line.type === 'COACH_RESPONSE') {
        responses.push({ scenario: trace.scenario, file, durationMs: durationByCid.get(line.cid), ...line });
      }
    }
  }
  return responses;
}

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[Math.max(0, idx)];
}

function summarize(responses) {
  const n = responses.length;
  const fallbacks = responses.filter((r) => r.fallback);
  const latencies = responses
    .map((r) => r.latencyMs ?? r.durationMs)
    .filter((v) => typeof v === 'number')
    .sort((a, b) => a - b);
  const sum = (key) => responses.reduce((acc, r) => acc + (r[key] ?? 0), 0);
  // Bedrock logs a granular input/output/cache breakdown; Vertex logs one `tokens` total (see
  // VertexCoachClient) — sum whichever each response actually has, per response, so a total is
  // available for both instead of always reading 0 for the provider that logs `tokens`.
  const totalTokens = responses.reduce((acc, r) => acc + (r.tokens ?? (r.inputTokens ?? 0) + (r.outputTokens ?? 0)), 0);
  return {
    n,
    fallbackRate: n === 0 ? 0 : fallbacks.length / n,
    meanLatencyMs: latencies.length === 0 ? 0 : Math.round(latencies.reduce((a, b) => a + b, 0) / latencies.length),
    p50LatencyMs: Math.round(percentile(latencies, 50)),
    p90LatencyMs: Math.round(percentile(latencies, 90)),
    inputTokens: sum('inputTokens'),
    outputTokens: sum('outputTokens'),
    cacheReadTokens: sum('cacheReadTokens'),
    cacheWriteTokens: sum('cacheWriteTokens'),
    totalTokens,
    fallbacks,
  };
}

function printSummary(label, summary) {
  console.log(`\n${label}`);
  console.log(`  n=${summary.n} fallbackRate=${(summary.fallbackRate * 100).toFixed(1)}%`);
  console.log(`  latencyMs: mean=${summary.meanLatencyMs} p50=${summary.p50LatencyMs} p90=${summary.p90LatencyMs}`);
  console.log(
    `  tokens: input=${summary.inputTokens} output=${summary.outputTokens} ` +
      `cacheRead=${summary.cacheReadTokens} cacheWrite=${summary.cacheWriteTokens} total=${summary.totalTokens}`
  );
  if (summary.fallbacks.length > 0) {
    console.log(`  fallbacks (${summary.fallbacks.length}):`);
    for (const f of summary.fallbacks) {
      console.log(`    - ${f.scenario} [${f.errorType ?? 'unknown'}] ${f.errorMsg ?? ''}`);
      if (f.rawResponseText) {
        console.log(`      rawResponseText: ${JSON.stringify(f.rawResponseText)}`);
      }
    }
  }
}

const targetDir = resolveTargetDir(process.argv[2]);
if (!fs.existsSync(targetDir)) {
  console.error(`No such reports directory: ${targetDir}`);
  process.exit(1);
}

const responses = loadCoachResponses(targetDir);
if (responses.length === 0) {
  console.error(`No COACH_RESPONSE entries found under ${targetDir}`);
  process.exit(1);
}

printSummary(`Overall — ${targetDir}`, summarize(responses));

const byScenario = new Map();
for (const r of responses) {
  if (!byScenario.has(r.scenario)) byScenario.set(r.scenario, []);
  byScenario.get(r.scenario).push(r);
}
for (const [scenario, group] of byScenario) {
  printSummary(`  ${scenario}`, summarize(group));
}
