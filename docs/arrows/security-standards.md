# Arrow: Security Standards

Cross-cutting security policy — spans Cloud Platform, User Management, and API Error Handling.

## Status

**OK** — 2026-04-22. All active security policies implemented across components.

## References

### HLD
- docs/high-level-design.md — "Public vs Authenticated Routes", "Backend vs Frontend Validation"

### LLD
- docs/llds/cloud-platform.md — IAM roles, API Gateway throttling, Cognito provisioning
- docs/llds/user-management.md — Email allowlist, JWT extraction, DynamoDB IDOR prevention
- docs/llds/api-error-handling.md — Input validation exception hierarchy
- docs/llds/game-lifecycle.md — Import validation (duplicates, solvability, uniqueness)

### EARS
- docs/specs/cloud-platform-specs.md — CP-IAM-*, CP-GW-* (throttling, JWT authorizer)
- docs/specs/user-management-specs.md — UM-AUTH-*, UM-ALLOW-* (allowlist, JWT)
- docs/specs/api-error-handling-specs.md — AEH-VAL-* (input validation)

## Architecture

**Purpose:** Define the security policy that all components must observe. This arrow has no single owner — it is maintained here so the rules are discoverable and traceable across components.

---

## IAM Least Privilege

**Rule:** Each Lambda has its own execution role with the minimum permissions required.

**Java Lambda role (`SudokuLambdaExecRole{suffix}`):**
- `AWSLambdaBasicExecutionRole` (CloudWatch Logs)
- DynamoDB Games table: `GetItem, PutItem, UpdateItem, Query`
- DynamoDB Players table: `GetItem, PutItem, UpdateItem` (no Query — single-key access only)
- `bedrock:InvokeModel` on the Claude Haiku inference-profile ARN and its foundation-model ARN (for the AI coaching feature — `SudokuCoachBedrockPolicy{suffix}`)
- No `cognito-idp:*` — the backend trusts JWT claims injected by API Gateway, never calls Cognito directly

**Image Recognition Lambda role (`SudokuImageRecognitionExecRole{suffix}`):**
- `AWSLambdaBasicExecutionRole`
- `bedrock:InvokeModel` on the Claude Haiku inference-profile ARN and its foundation-model ARN only

**No hardcoded credentials:** AWS CLI uses local profiles (`sandbox`). CI/CD uses OIDC role assumption (`AWS_DEPLOY_ROLE_ARN`). Google OAuth credentials passed as sensitive Terraform variables, injected via GitHub Actions secrets.

---

## Bill Protection

**Rule:** Throttle all API Gateway routes and enforce Lambda timeouts so runaway loops or DoS attacks cannot generate unbounded cost.

**API Gateway throttle (global):**

| Setting | Value |
| --- | --- |
| Burst limit | 50 requests |
| Rate limit | 25 req/sec |

The `/puzzles/generate` endpoint is the most compute-intensive public route and the most attractive DoS target. A route-level override tightening rate/burst further is an open option.

**Lambda timeouts:**

| Lambda | Timeout |
| --- | --- |
| Java (Quarkus) | 8 seconds |
| Image Recognition (Python) | 60 seconds |

Puzzle generation is near-instant; the Java timeout is set to 8s to allow for SnapStart resume overhead while still failing fast on hangs.

---

## Input Validation Policy

**Rule:** The backend validates all client-submitted data independently. The frontend performs optimistic validation for UX but is never trusted for correctness.

**What the backend validates:**
- Grid imports: duplicate check, solvability (`solveGrid`), uniqueness (`hasSingleSolution`)
- Player profile updates: non-null field required; `displayName` 1–50 chars when provided
- Game requests: `userId` comes exclusively from the JWT `sub` claim — never from the request body or path

**What the frontend validates (optimistic only):**
- `completedNumbers` set disables fully-placed digits in the number pad
- Auto-validation on grid completion (triggers `POST /puzzles/validate`)
- Error cell highlighting based on validation response

**IDOR prevention:** DynamoDB Game queries always supply both `userId` (from JWT) and `gameId` (from path). A query for `gameId` without the matching `userId` will return nothing — User A cannot retrieve User B's game.

---

## Authentication Boundary

**Rule:** No route in `/api/v1/games/*` or `/api/v1/players/*` is accessible without a valid Cognito JWT. API Gateway enforces this before the Lambda is invoked.

**Routes matrix:** See `docs/llds/user-management.md` — Route Authorizer Matrix.

**No wildcard CORS in production:** The CORS two-step pattern (see `docs/llds/cloud-platform.md`) ensures `allow_origins` is tightened to the exact Amplify URL after deploy. Terraform applies a baseline wildcard only to resolve the circular dependency at apply time.

---

## Logging Policy

**Rule:** Log what is needed for diagnosis. Do not log what is not needed for diagnosis. Never log credentials, session tokens, or raw HTTP `Authorization` headers.

This section is the policy and threat-model rationale for *what category* of data may be
logged. For the exact field-by-field schema of every structured log line (`COACH_*`,
`NUMBER`, `HINT_*`, …) and the `pid`/`cid` correlation model, see `docs/llds/observability.md`.

**What may be logged at INFO level in production:**

| Category | Acceptable | Not acceptable |
|----------|-----------|----------------|
| Auth events | userId (Cognito `sub`), timestamp, HTTP status | JWT token, email, Cognito session |
| Game events | gameId, difficulty, outcome, score | currentGrid, solutionGrid contents |
| Coach events (metadata) | cid, modelId, technique, token counts, latency, fallback flag | — |
| Coach events (content) | userMessage, aiMessage, board grid, candidatesGrid | — (these are user-generated but not sensitive for this app's threat model — see note below) |
| Puzzle-play events | pid (gameId), userId, cell coordinates, placed digit, move correctness, hint technique/rank | — (gameplay actions; non-sensitive under this threat model) |
| API errors | HTTP status, error code, correlation ID | Exception stack traces in 5xx response body (fine in logs, never in response) |

**Note on coach conversation content:** This app is a personal project operated for a small, known allowlisted user set. Users are not anonymous. Logging full coach conversation turns (user prompts, AI responses, and board state) is acceptable under this threat model but requires conscious acknowledgement before implementation:
- Content logs must go to the **same CloudWatch log group** as other Lambda logs (`/aws/lambda/sudoku{suffix}`), not a separate store.
- Log group retention must be set explicitly in Terraform (no indefinite retention). Current policy: 30 days for all log groups.
- If this app is ever opened to non-allowlisted or anonymous users, coach content logging must be revisited before that change ships.

**Log verbosity by environment:**

| Environment | Level | coach content |
|-------------|-------|---------------|
| Production (`default`, `rc-*`) | INFO | Yes (opted in per above) |
| Dev (local, `quarkus:dev`) | DEBUG | Yes |
| Test | WARN | No (test logs should be silent unless a test fails) |

**No external analytics:** No user behaviour data is sent to third-party analytics services (Mixpanel, Segment, etc.). CloudWatch is the only log sink.

---

## Security Checklist

Before shipping auth changes to production, verify:

- [ ] No `allow_origins = ["*"]` anywhere (API Gateway CORS, Cognito callback URLs)
- [ ] JWT Authorizer attached to all `/games/*` and `/players/*` routes
- [ ] `userId` comes exclusively from JWT `sub`, never from request body or path
- [ ] DynamoDB queries always include `userId` as partition key
- [ ] `SudokuPlayers` table grants isolated from `SudokuGames` in IAM policy
- [ ] Google credentials passed as sensitive Terraform variables — never committed
- [ ] Refresh token rotation enabled on the Cognito App Client
- [ ] No `cognito-idp:*` permissions on the Lambda execution role
