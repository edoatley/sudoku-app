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
