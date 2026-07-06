# AI Coach Rate Limiting per User

**Summary:** Limit each authenticated user to N coach calls per hour/day via a DynamoDB counter with TTL, returning 429 when exceeded, to prevent abuse and runaway Bedrock costs.

**Branch context:** `rc-ai-coach` — AI coach feature freshly deployed; no usage-based controls exist yet.

## Why deferred

Out of scope for the initial feature PR. The coach is live and auth-protected (Cognito JWT required) but there is no per-user call limit — a single user could spam Bedrock calls indefinitely.

## Context

**Relevant files:**
- `backend/src/main/java/com/sudoku/puzzle/CoachResource.java` — POST `/ai/coach` entry point; inject `SecurityContext` here (same pattern as `GameResource.java` line 42–46)
- `backend/src/main/java/com/sudoku/game/GameResource.java` — shows the `@Context SecurityContext` + `securityContext.getUserPrincipal().getName()` pattern for getting the Cognito `sub`
- `infra/dynamodb.tf` — defines `sudoku_games` and `sudoku_players` tables (PAY_PER_REQUEST billing); add a `sudoku_coach_rate_limits` table here
- `docs/specs/sudoku-coach-specs.md` — coach EARS specs; no rate limit requirement exists yet (add one)
- `infra/lambda.tf` — Lambda env vars; add `COACH_RATE_LIMIT_TABLE_NAME` here

**Current state:**
`CoachResource.POST /ai/coach` calls `coachService.coach(request)` with no identity check and no call counting. The JWT is validated by API Gateway before Lambda invocation, so the Cognito `sub` is available via `SecurityContext.getUserPrincipal().getName()`. Two DynamoDB tables exist (`sudoku_games`, `sudoku_players`), both PAY_PER_REQUEST — a third table for rate-limit counters fits the same pattern. No CloudWatch alarms exist on coach log volume or Bedrock cost.

**Key constraints:**
- DynamoDB TTL is the right expiry mechanism — no cron or scheduled Lambda needed
- The counter must be atomic: use `UpdateItem` with `ADD` on a numeric attribute, not read-then-write
- Lambda timeout is 8 s total; the DynamoDB counter check must complete in < 200 ms (it's a single-item write)
- Quarkus OIDC `application-type=service` — the `sub` claim is the Cognito user UUID, available via `SecurityContext`
- Return `429 Too Many Requests` with a `Retry-After` header (seconds until window resets)

## What to do

1. **Add DynamoDB table** in `infra/dynamodb.tf`: partition key `userId` (S), sort key `window` (S, e.g. `"2026-07-05T16"` for hourly), attribute `callCount` (N), TTL attribute `expiresAt` (N, Unix epoch). Add `COACH_RATE_LIMIT_TABLE_NAME` to Lambda env vars in `infra/lambda.tf`.

2. **Create `CoachRateLimiter.java`** in `backend/src/main/java/com/sudoku/puzzle/`: `@ApplicationScoped` CDI bean that calls DynamoDB `UpdateItem` with `ADD callCount 1` and `ConditionExpression: callCount < :limit`. Returns `true` if allowed, `false` if limit exceeded (catches `ConditionalCheckFailedException`).

3. **Inject into `CoachResource`**: add `@Context SecurityContext securityContext` and `@Inject CoachRateLimiter rateLimiter`. At the top of `coach()`, extract `userId`, call `rateLimiter.check(userId)`, return `Response.status(429).header("Retry-After", secondsUntilReset).build()` if denied.

4. **Add COACH_RATE_LIMIT_TABLE_NAME read permission** to the Lambda IAM role in `infra/iam.tf` (dynamodb:UpdateItem, dynamodb:GetItem on the new table ARN).

5. **Add CloudWatch metric filter** on `/aws/lambda/sudoku-rc-ai-coach` log group: filter pattern `{ $.type = "COACH_RESPONSE" }`, metric name `CoachCallCount`. Add an alarm at e.g. 500 calls/hour to SNS → email alert.

6. **Add EARS spec** to `docs/specs/sudoku-coach-specs.md`: `SC-RL-001` through `SC-RL-003` covering the limit, the 429 response, and the alarm threshold.

## Acceptance criteria

- [ ] A single user cannot make more than N calls in the defined window (verify with a script or test)
- [ ] 429 response includes `Retry-After` header with correct seconds-to-reset value
- [ ] DynamoDB items expire automatically (TTL set correctly; verify with `aws dynamodb scan`)
- [ ] CloudWatch alarm fires when call volume exceeds threshold (test with `aws cloudwatch set-alarm-state`)
- [ ] IAM policy allows only the Lambda role to write to the rate-limit table

## Related specs / docs

- [`docs/specs/sudoku-coach-specs.md`](../specs/sudoku-coach-specs.md) — add SC-RL-001..003 here
- [`docs/llds/`](../llds/) — AI coach LLD; add rate limiting section
- `infra/dynamodb.tf` — table definitions to extend
