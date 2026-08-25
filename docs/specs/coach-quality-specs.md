# Coach Quality Diagnostic Runner — EARS Specifications

Covers `ui/tests/coach-quality/` — the opt-in diagnostic runner described in
`docs/arrows/testing-strategy.md` (Layer 2c). Not a conventional pass/fail suite: the
requirements below govern the correctness of the trace/report it produces, since that report
is the primary deliverable.

## Coach-turn correlation

- [x] **CQ-RUN-001**: When a scenario's `ask` action receives a 204 response (puzzle already
  solved), the runner shall not wait for a COACH_REQUEST/COACH_RESPONSE log pair and shall not
  consume a coach-turn index, since the backend never calls Bedrock on that path.
- [x] **CQ-RUN-002**: When a coach-turn log pair cannot be obtained (204-skip or a
  `waitForCoachPair` timeout), assertions that depend on it (`coachFallback`,
  `hintMatchesCoachTechnique`) shall report the specific reason as their `actual` value rather
  than silently evaluating `undefined`.
- [x] **CQ-LOG-001**: The system shall correlate COACH_REQUEST/COACH_RESPONSE log lines for a
  game FIFO — each response pairs with the oldest outstanding request for that `pid` — so a
  request is never silently dropped if another request is logged before its response.
- [x] **CQ-LOG-002**: The system shall bound backend log reads to lines emitted at or after the
  current scenario's start time, rather than re-reading the backend container's entire log
  history on every poll.
- [x] **CQ-LOG-003**: When `COACH_QUALITY_API_URL` targets a deployed (non-local) backend, the
  system shall read the structured log lines from GCP Cloud Logging (the deployed Cloud Run
  service) rather than the local container's stdout, preserving the FIFO pairing and since-bound
  reads of CQ-LOG-001/002; and it shall tolerate Cloud Logging's ingestion propagation delay
  with a longer, env-overridable poll timeout than the local path.

## Assertions

- [x] **CQ-AST-001**: The system shall provide a `coachResponseType` assertion kind that checks
  the most recent `ask`'s correlated `COACH_RESPONSE` log line's `responseType` field (the
  schema-enforced category from SC-BE-028) against an expected value, so scenarios can assert
  on the pedagogical intent of a non-deterministic coach reply without substring-matching its
  prose text.

## Report integrity

- [x] **CQ-RPT-001**: When the final game-state fetch fails after a scenario's actions
  complete, the system shall record the failure reason in the trace and report, rather than
  silently omitting the final-board section.

## Board validity

- [x] **CQ-DAT-001**: The system shall determine board validity from the grid returned by the
  backend REST API (`GET /games/{id}`); it shall not require a direct DynamoDB read.

## Scenario fixtures

- [x] **CQ-SCN-001**: Scenarios sharing an identical starting grid shall reference one shared
  fixture rather than duplicating the grid literal.
