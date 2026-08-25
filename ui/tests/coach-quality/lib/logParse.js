/**
 * Extracts a structured JSON log object from a single backend log line of the form
 * "<prefix> {json}" — the Quarkus console format surfaced identically by `docker compose logs`
 * (local), GCP Cloud Logging `textPayload` (Cloud Run), and CloudWatch (AWS Lambda). Takes
 * everything from the first '{' onward and parses it as JSON.
 *
 * Returns null for lines that carry no structured event (stack traces, Cloud Run request logs,
 * blank lines) so callers can filter them out.
 */
export function parseStructuredLine(line) {
  const start = line.indexOf('{');
  if (start === -1) return null;
  try {
    return JSON.parse(line.slice(start));
  } catch {
    // Not a JSON log line (e.g. a stack trace fragment) — skip.
    return null;
  }
}
