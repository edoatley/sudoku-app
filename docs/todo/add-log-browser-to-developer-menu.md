# Add a log browser to the developer/admin menu

**Summary:** Let a developer/admin view the CloudWatch log lines (puzzle-play + coach)
correlated to the game currently open in the UI, from a panel in the existing dev/admin menu,
instead of manually running AWS CLI commands.

**Branch context:** `rc-coach-structured-output` — was validating PR #134 (Bedrock structured
output + `responseType`) against the RC deployment by hand-crafting `aws logs filter-log-events`
commands (see the manual test script produced earlier this session). That workflow is clunky
enough to want a built-in tool.

## Why deferred

Raised as a "would be nice" while manually testing an unrelated PR — out of scope for that
branch, and non-trivial enough (new IAM permissions, new endpoint, new UI) to need its own
session rather than folding into an in-flight PR.

## Context

**Relevant files:**
- `backend/src/main/java/com/sudoku/developer/DevResource.java` — existing `/dev` endpoints,
  deliberately not `@IfBuildProfile`-guarded since the Lambda is built once and shared by every
  Terraform workspace (see its class Javadoc for the reasoning) — a log-browser endpoint should
  probably follow the **admin-gated** pattern below instead, since it needs new IAM permissions.
- `backend/src/main/java/com/sudoku/admin/AdminDataResource.java` — the existing admin-only
  pattern (`@AdminOnly`, `/admin/data`, gated by the `administrators` Cognito group via
  `AdminAuthorizationFilter`) that a `/admin/logs` endpoint should mirror.
- `ui/src/components/DevDataDialog.jsx` — existing dev-menu dialog to model a new
  `LogBrowserDialog.jsx` on.
- `ui/src/App.jsx` (~line 34, ~301-404) — `DEV_TOOLS`/`admin` flags gate `onDevData`/
  `DevDataDialog` mounting; a new menu entry + dialog wires in the same way.
- `docs/llds/observability.md` — defines the `pid` (gameId) correlation model and the full
  field catalogue for `COACH_REQUEST`/`COACH_RESPONSE`/puzzle-play log lines. This is what a
  "logs for the current game" query filters on.
- `infra/iam.tf` — Lambda's IAM role/policies; querying CloudWatch Logs from inside the Lambda
  itself needs a new `logs:FilterLogEvents` (+ `logs:DescribeLogStreams`) grant scoped to its
  own log group, which doesn't exist today.

**Current state:**
No backend endpoint reads CloudWatch Logs today. All log inspection is either manual (AWS
Console), via one-off scripts (`scripts/logs/download-puzzle-logs.sh`,
`scripts/github/coach-smoke-test.sh`'s inline `aws logs filter-log-events` calls), or via the
coach-quality test harness's `ui/tests/coach-quality/lib/dockerLogs.js` (which reads **local
Docker container** logs, not CloudWatch, and only works against `docker-compose` — not
applicable to a deployed environment). The dev/admin menu (`DevDataDialog`) only exposes
DynamoDB table contents, not logs.

**Key constraints:**
- Every log line already carries `pid` (the gameId) — see `docs/llds/observability.md` and
  `SC-BE-020`/`GL-BE-040..047` for the exact fields per line type. Correlating "logs for the
  current game" is a `pid` filter, no new correlation mechanism needed.
- The Lambda's own execution role would need a new IAM grant to call `logs:FilterLogEvents`
  against its own log group (`/aws/lambda/sudoku{suffix}`) — this is a new capability, not
  reusing an existing permission.
- Per `docs/arrows/security-standards.md`, coach/puzzle-play log content is judged non-sensitive
  under this app's threat model (small, known, non-anonymous user set) — but exposing an
  in-app log viewer to any authenticated user (vs. admin-only) would be a materially different
  exposure than "logged, and an admin can pull it via AWS CLI if needed." Recommend gating
  behind `@AdminOnly` like `AdminDataResource`, not `DEV_TOOLS` alone.

## What to do

1. Add `logs:FilterLogEvents` + `logs:DescribeLogStreams` to the Lambda's IAM policy in
   `infra/iam.tf`, scoped to its own log group ARN (`/aws/lambda/sudoku${local.suffix}`).
2. Add `AdminLogsResource` (`/admin/logs`, `@AdminOnly`, mirroring `AdminDataResource`) with a
   `GET /admin/logs?pid={gameId}` endpoint that calls CloudWatch Logs `FilterLogEvents` with a
   `"pid":"{gameId}"` filter pattern (same pattern already used in
   `scripts/github/coach-smoke-test.sh`) and returns parsed JSON log lines, newest-first.
3. Add `LogBrowserDialog.jsx` modeled on `DevDataDialog.jsx`: fetches `/admin/logs?pid=...` for
   the currently-open game, renders each line (type, timestamp, key fields) — reuse the
   coach-quality harness's Markdown-transcript rendering approach
   (`ui/tests/coach-quality/lib/report.js`) as a reference for a readable per-line format.
4. Wire a new menu entry in `App.jsx` next to `onDevData`, gated on `admin` (not `DEV_TOOLS`,
   per the constraint above) — pass the current `gameId` down to the dialog.
5. Write EARS specs (new file or fold into an existing admin-facing spec file) and update
   `docs/llds/observability.md` to note the new consumer of the `pid` correlation model.

## Acceptance criteria

- [ ] An admin user can open the dev/admin menu, click a new "Logs" entry, and see every
      `COACH_REQUEST`/`COACH_RESPONSE`/puzzle-play log line for the currently-open game,
      ordered by timestamp
- [ ] A non-admin authenticated user cannot reach `/admin/logs` (403, matching
      `AdminDataResource`'s existing gating)
- [ ] The Lambda's IAM policy grants only `logs:FilterLogEvents`/`logs:DescribeLogStreams` on
      its own log group — not a broader CloudWatch Logs grant
- [ ] Works against a real deployed environment (RC or prod), not just DynamoDB Local —
      CloudWatch Logs has no local-Docker equivalent to test against

## Related specs / docs

- [`docs/llds/observability.md`](../llds/observability.md) — `pid`/`cid` correlation model and
  full field catalogue this feature reads
- [`docs/arrows/security-standards.md`](../arrows/security-standards.md) — logging content
  policy and threat model this feature's access control should respect
- [`docs/llds/cloud-platform.md`](../llds/cloud-platform.md) — IAM/Terraform conventions for
  the new log-read permission
