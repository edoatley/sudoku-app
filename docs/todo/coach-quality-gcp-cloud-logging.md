# Add GCP Cloud Logging support to the coach-quality harness

**Summary:** Let `ui/tests/coach-quality/`'s diagnostic runner read structured
`COACH_REQUEST`/`COACH_RESPONSE` log lines from GCP Cloud Logging, so it can produce a real
report against a deployed `rcg-*`/GCP environment, not just a local docker-compose stack.

**Branch context:** `feat/coach-quality-remote-vertex-compare` — built while trying to validate
the Vertex AI coach (CP-GCP-090) by deploying an `rcg-*` workspace and running the harness
against it directly.

## Why deferred

Discovered mid-attempt: wired `COACH_QUALITY_API_URL`/`COACH_QUALITY_AUTH_TOKEN` auth support
into `apiClient.js` and deployed a real `rcg-vertex` GCP workspace, only to find every scenario
fails at log correlation — `lib/dockerLogs.js` shells out to `docker compose logs backend`
unconditionally, which doesn't exist for a remote target. The `/ai/coach` calls themselves had
already fired (real cross-cloud Bedrock spend) before the failure. Redirected to a local-ADC
comparison instead (`docker-compose.coach-quality-vertex.yml`) to unblock the immediate Vertex
validation without this larger feature. This todo is that untaken path.

## Context

**Relevant files:**
- `ui/tests/coach-quality/lib/dockerLogs.js` — `fetchLogLines()`/`logsForGame()`, the only place
  structured logs are read. Hardcoded to `docker compose -f docker-compose.test.yml -f
  docker-compose.coach-quality.yml logs backend`. Its own doc comment already names the
  CloudWatch equivalent for AWS (`scripts/logs/download-puzzle-logs.sh`,
  `scripts/logs/download-coach-logs.sh`) as the pattern for "a deployed environment" — no GCP
  Cloud Logging equivalent exists anywhere in the repo.
- `ui/tests/coach-quality/lib/runner.js` (~line 276) — calls `logsForGame` mid-scenario to
  correlate each coach turn; this is where remote runs currently fail.
- `ui/tests/coach-quality/lib/apiClient.js` — already supports `COACH_QUALITY_API_URL` +
  `COACH_QUALITY_AUTH_TOKEN` for pointing requests at a remote backend (this part works).
- `scripts/local/coach-quality-remote-compare.sh` — the wrapper script built for this; currently
  produces "No COACH_RESPONSE entries found" for every run because of the gap above. Don't fix
  it in isolation — the fix belongs in `dockerLogs.js`, which this script depends on.
- `docs/todo/add-log-browser-to-developer-menu.md` — a related, separately-scoped deferred item
  (an admin-facing CloudWatch log viewer for AWS). Different consumer, same underlying gap
  (no GCP log-read capability exists yet either).

**Current state:**
`fetchLogLines()` always shells out to local `docker compose logs`. There is no code path that
reads GCP Cloud Logging at all — not from the harness, not from any script (AWS has
`scripts/logs/download-*.sh` via CloudWatch; GCP has nothing equivalent). A scenario run against
`COACH_QUALITY_API_URL` pointed at a real `rcg-*`/prod GCP backend gets real `/ai/coach`
responses (auth now works via `COACH_QUALITY_AUTH_TOKEN`) but then fails when the runner tries
to correlate the coach turn against logs it can't fetch.

**Key constraints:**
- Every structured log line shares a `pid` (gameId) — see `docs/llds/observability.md`. The GCP
  read path needs to filter by `pid` and a time window (`--since`), matching what
  `fetchLogLines(since)` already does for docker logs (CQ-LOG-002).
- GCP Cloud Logging has propagation delay (unlike local docker logs, called out in
  `dockerLogs.js`'s own doc comment) — the correlation wait/retry logic in `runner.js` may need
  a longer timeout or backoff when the log source is remote.
- `gcloud logging read` (CLI) or the Cloud Logging REST/client library are the two implementation
  options — the CLI is simpler and mirrors the AWS CLI-based `download-coach-logs.sh` pattern,
  but shells out per-poll like `dockerLogs.js` does today; the client library avoids a subprocess
  per call but adds a new dependency to a test-only path.

## What to do

1. Decide the read mechanism (CLI shell-out vs client library) — recommend starting with
   `gcloud logging read` via `execFileSync`, mirroring `dockerLogs.js`'s existing subprocess
   pattern, since it needs zero new dependencies.
2. Add a GCP-specific fetch function alongside `fetchLogLines`/`logsForGame` in `dockerLogs.js`
   (or a new sibling module, e.g. `cloudLoggingClient.js`), selected when `COACH_QUALITY_API_URL`
   is set and points at a non-local host — filter by `pid`, `resource.labels.service_name`
   (the Cloud Run service), and a `--since` timestamp.
3. Handle Cloud Logging propagation delay: either poll with a longer timeout than the local path,
   or make the timeout configurable via an env var.
4. Update `scripts/local/coach-quality-remote-compare.sh`'s doc comment and
   `ui/tests/coach-quality/README.md` — remove the "partial — no log correlation yet" caveat once
   this lands.
5. Consider whether this should reuse or share logic with the deferred admin log browser
   (`docs/todo/add-log-browser-to-developer-menu.md`) — that's a backend endpoint for AWS
   CloudWatch; this is a client-side test-harness read from GCP Cloud Logging. Likely stay
   separate (different clouds, different consumers) but worth a quick check before building.

## Acceptance criteria

- [x] `COACH_QUALITY_API_URL` pointed at a deployed GCP backend produces a full report
      (fallback/responseType/token counts populated, not "No COACH_RESPONSE entries found")
- [x] `scripts/local/coach-quality-remote-compare.sh` run against a live `rcg-*` workspace
      completes without the log-correlation failures seen in this session
- [x] Local docker-compose runs are unaffected (no behavior change to the existing working path)

## Validation evidence (2026-08-25)

Implemented `lib/cloudLoggingClient.js` (`gcloud logging read`, zero new deps) + shared
`lib/logParse.js`; `lib/dockerLogs.js` selects the source by target and uses longer remote poll
defaults. Validated against a live `rcg-cq-gcplog` Vertex workspace
(`sudoku-rcg-cq-gcplog-...run.app`): the report populated real per-turn data
(`fallbackRate=0.0%`, token/latency counts) instead of "No COACH_RESPONSE entries found". The
multi-turn `deep-escalation-ladder` correlated all 4 pairs from Cloud Logging
(`nudge→focus-hint→reveal-answer→reveal-answer`, 0 fallbacks). Spec `CQ-LOG-003` added `[x]`.

Two side-findings (not log-correlation, left as separate concerns): `off-topic-message`'s
`coachLogContains "puzzle"` assertion is Bedrock-prose-specific and fails on Gemini's redirect
wording; and cold deployed multi-turn scenarios blew past Playwright's 60s per-test timeout, so the
per-test timeout is now env-configurable (`COACH_QUALITY_TEST_TIMEOUT_MS`, raised to 240s by the
remote wrapper). Unit tests cover parsing + source/service/project resolution.

## Related specs / docs

- [`docs/specs/coach-quality-specs.md`](../specs/coach-quality-specs.md) — `CQ-LOG-001`/`CQ-LOG-002`
  govern the correlation behavior this needs to preserve for a remote source
- [`docs/arrows/testing-strategy.md`](../arrows/testing-strategy.md) — Layer 2c, the coach-quality
  diagnostic runner's overall design
- [`docs/todo/add-log-browser-to-developer-menu.md`](add-log-browser-to-developer-menu.md) —
  related but separate: an in-app CloudWatch (AWS) log viewer, not this harness
