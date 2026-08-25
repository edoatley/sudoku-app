/**
 * Reads structured backend log lines from GCP Cloud Logging for a deployed Cloud Run backend —
 * the GCP counterpart of dockerLogs.js's local `docker compose logs` read, and of
 * scripts/logs/download-coach-logs.sh's CloudWatch read for AWS. dockerLogs.js delegates here
 * when COACH_QUALITY_API_URL points at a non-local host, so the runner correlates coach turns
 * against a real deployed environment the same way it does against the local stack.
 *
 * Zero new dependencies: shells out to `gcloud logging read` via execFileSync, mirroring
 * dockerLogs.js's subprocess style (and the AWS CLI shell-out in download-coach-logs.sh).
 * Requires the gcloud CLI on PATH and an authenticated ADC/user session with logging.view on
 * the target project.
 *
 * Cloud Logging has ingestion propagation delay (seconds to ~a minute), unlike instant local
 * docker logs — waitForCoachPair (dockerLogs.js) compensates with a longer default timeout on
 * the remote path.
 */
import { execFileSync } from 'node:child_process';
import { parseStructuredLine } from './logParse.js';

// Hosts that mean "the local docker stack" — anything else is treated as a deployed backend.
const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '0.0.0.0', '::1', '[::1]']);

// Generous upper bound on lines per read. A coach-quality run drives one deployed workspace
// serially as a single user, so a since-bounded window holds far fewer structured lines than
// this; the cap only guards against an unexpectedly chatty window.
const LOG_READ_LIMIT = 1000;

/** True when COACH_QUALITY_API_URL targets a deployed (non-local) backend. */
export function isRemoteLogSource() {
  const url = process.env.COACH_QUALITY_API_URL;
  if (!url) return false;
  try {
    return !LOCAL_HOSTS.has(new URL(url).hostname);
  } catch {
    return false;
  }
}

/**
 * The Cloud Run service name whose logs to read. Explicit via COACH_QUALITY_GCP_SERVICE, else
 * derived from the Cloud Run default hostname `<service>-<hash>-<regionCode>.a.run.app`.
 */
export function resolveServiceName() {
  const explicit = process.env.COACH_QUALITY_GCP_SERVICE;
  if (explicit) return explicit;
  const host = new URL(process.env.COACH_QUALITY_API_URL).hostname;
  const match = host.match(/^(.+)-[a-z0-9]+-[a-z0-9]{2}\.a\.run\.app$/);
  if (!match) {
    throw new Error(
      `cloudLoggingClient: cannot derive a Cloud Run service name from host "${host}" — ` +
        'set COACH_QUALITY_GCP_SERVICE explicitly.'
    );
  }
  return match[1];
}

/** The GCP project to read logs from. Explicit via COACH_QUALITY_GCP_PROJECT, else `gcloud config`. */
export function resolveProject() {
  const explicit = process.env.COACH_QUALITY_GCP_PROJECT;
  if (explicit) return explicit;
  const project = execFileSync('gcloud', ['config', 'get-value', 'project'], { encoding: 'utf8' }).trim();
  if (!project || project === '(unset)') {
    throw new Error(
      'cloudLoggingClient: no GCP project configured — set COACH_QUALITY_GCP_PROJECT or run ' +
        '`gcloud config set project <id>`.'
    );
  }
  return project;
}

/**
 * All structured backend log lines for the deployed service, bounded to `since` (an RFC3339
 * timestamp — typically the scenario's startedAt). Mirrors dockerLogs.js's fetchLogLines: it
 * returns every parseable structured line and leaves pid/type filtering to the callers
 * (logsForGame / coachPairsForGame), so the correlation logic (CQ-LOG-001) is identical across
 * sources.
 *
 * @spec CQ-LOG-002, CQ-LOG-003
 */
export function fetchLogLinesGcp(since) {
  const service = resolveServiceName();
  const project = resolveProject();

  // Cloud Run writes each stdout line to textPayload as the full Quarkus log line
  // "<ts> LEVEL [logger] (thread) {json}". Ordering asc keeps lines chronological so the FIFO
  // request/response pairing in coachPairsForGame holds, exactly as the docker stdout stream does.
  let filter = `resource.type="cloud_run_revision" AND resource.labels.service_name="${service}"`;
  if (since) filter += ` AND timestamp>="${since}"`;

  const raw = execFileSync(
    'gcloud',
    [
      'logging',
      'read',
      filter,
      '--project',
      project,
      '--format',
      'value(textPayload)',
      '--order',
      'asc',
      '--limit',
      String(LOG_READ_LIMIT),
    ],
    { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }
  );

  const lines = [];
  for (const line of raw.split('\n')) {
    const parsed = parseStructuredLine(line);
    if (parsed) lines.push(parsed);
  }
  return lines;
}
