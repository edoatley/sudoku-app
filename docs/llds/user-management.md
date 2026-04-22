# User Management

**Created**: 2026-04-18
**Status**: Complete

## Context and Current State

The User Management component covers everything related to player identity: profile creation and retrieval, JWT-based authentication, email allowlisting, and the cross-cutting filters that enforce security policies on every request. It also includes the developer infrastructure that makes local testing possible without a live Cognito pool.

Files: `player/PlayerResource.java`, `player/PlayerService.java`, `player/PlayerServiceImpl.java`, `player/PlayerRepository.java`, `player/DynamoDbPlayerRepository.java`, `player/PlayerItem.java`, `player/PlayerProfile.java`, `auth/AllowedUsersFilter.java`, `developer/DevUserFilter.java`, `developer/DevDatabaseInitializer.java`, `cors/CorsFilter.java`, `logging/ApiLoggingFilter.java`.

## Authentication Architecture

Authentication is handled at two layers:

```text
                    Internet
                       │
                       ▼
              API Gateway JWT Authorizer
              (validates Cognito JWT: iss + aud)
                       │ valid token passes through
                       ▼
              AllowedUsersFilter (JAX-RS ContainerRequestFilter)
              (checks email claim against app.allowed.emails)
                       │ email on allowlist (or allowlist disabled)
                       ▼
              GameResource / PlayerResource / PuzzleResource
              (extracts userId/email from SecurityContext / JsonWebToken)
```

In **dev/IT/test** profiles, `DevUserFilter` replaces the JWT layer:

```text
              DevUserFilter (ContainerRequestFilter, @IfBuildProfile dev/it/test)
              (if no Authorization header → inject mock SecurityContext with userId="local-dev-user")
                       │
                       ▼
              AllowedUsersFilter
              (allowlist empty in dev → passes all requests)
```

## Player Profile

### Lazy Creation Pattern

Player profiles are not created at registration — there is no registration event. Cognito handles identity; the backend creates a `PlayerProfile` record the first time `GET /players/me` is called:

```text
GET /players/me
  → playerService.getOrCreateProfile(userId, email, displayName)
      → playerRepository.findById(userId)
          if found → return existing profile (unchanged, even if email/name changed)
          if not found → create new PlayerProfile(userId, email, displayName, avatarKey=null, now, now)
                       → playerRepository.upsert(newProfile)
                       → return new profile
```

### Profile Update Pattern

```text
PATCH /players/me { displayName?, avatarKey? }
  → playerService.updateProfile(userId, request)
      → validate: both null → InvalidPlayerUpdateException (400)
      → validate: displayName blank or >50 chars → InvalidPlayerUpdateException (400)
      → playerRepository.findById(userId)
          if not found → PlayerNotFoundException (404)
          if found → merge non-null fields; set updatedAt = now
                   → playerRepository.upsert(mergedProfile)
                   → return updated profile
```

Email and display name come from JWT claims. Once created, the profile is returned as-is on subsequent calls — there is no profile-update flow in the current implementation.

### JWT Claim Extraction

`PlayerResource` extracts claims from `SecurityIdentity` using a two-step fallback:

1. Cast principal to `JsonWebToken` → `jwt.getClaim("email")` / `jwt.getClaim("name")`
2. Fall back to `identity.getAttribute(claimName)` if JWT cast fails

This dual approach handles both Quarkus OIDC (which provides `JsonWebToken`) and the dev mock filter (which provides a plain `Principal`).

### REST API

| Method | Path | Auth | Request body | Response | Notes |
| --- | --- | --- | --- | --- | --- |
| GET | `/players/me` | Required | — | `PlayerProfile` | Creates profile if first visit |
| PATCH | `/players/me` | Required | `PlayerUpdateRequest` | `PlayerProfile` | Both fields optional; at least one required |

`PlayerUpdateRequest` is a record with two optional fields:
- `displayName` — string; trimmed; 1–50 chars when provided
- `avatarKey` — string; any non-null value accepted (client-defined icon name)

Both null → 400. No-op PATCH (empty payload) → 400.

## DynamoDB Schema

Table: `SudokuPlayers{suffix}` (name injected via `sudoku.dynamodb.players-table-name`)

| Attribute | DynamoDB Type | Key Role | Notes |
| --- | --- | --- | --- |
| `userId` | String | Partition Key | Cognito `sub` claim |
| `email` | String | — | From Cognito JWT; read-only after creation |
| `displayName` | String | — | From Cognito JWT name claim; updatable via PATCH |
| `avatarKey` | String | — | Client-defined icon name; empty string stored as null; updatable via PATCH |
| `createdAt` | String | — | ISO-8601 UTC |
| `updatedAt` | String | — | ISO-8601 UTC; updated on every PATCH |

No sort key — one profile per user. `upsert` is a DynamoDB `PutItem` (full overwrite).

`PlayerItem` is a mutable `@DynamoDbBean` class with getters/setters (required by the Enhanced Client). Conversion is symmetric:

```text
PlayerProfile (DTO, immutable record)
    ↕  PlayerItem.from(profile) / item.toPlayerProfile()
PlayerItem (entity, mutable)
```

`PlayerItem.from()` substitutes empty string for null email, displayName, or avatarKey — DynamoDB does not store null string attributes. `toPlayerProfile()` converts empty string back to null for avatarKey so callers receive a clean null rather than an empty string.

## Email Allowlist Filter

`AllowedUsersFilter` is a JAX-RS `ContainerRequestFilter` that runs on every request when the allowlist is non-empty.

**Configuration:** `app.allowed.emails` (comma-separated). Empty by default in all profiles except production.

**Production allowlist** (from `application.properties`):

```
edoatley@gmail.com, hanoatley@gmail.com, edoatley2@gmail.com, edoatley+sudokuapp@icloud.com
```

**Filter logic:**

```text
filter(request):
  if allowedEmails.isEmpty() → pass (allowlist disabled)
  if identity.isAnonymous() → pass (public route, auth not yet checked)
  extract email from JWT (JsonWebToken.getClaim("email") or identity.getAttribute("email"))
  if email == null OR email not in allowedEmails:
    abort(403, {"error": "Access denied"})
```

The anonymous check ensures public routes (`/puzzles/*`, `/health`) are not blocked even when a non-authenticated request reaches this filter.

## Cross-Cutting Request Filters

### DevUserFilter (`@IfBuildProfile(anyOf = {"dev", "it", "test"})`)

Compiled out of production builds entirely. When active:

- If `Authorization` header is **absent** → injects a mock `SecurityContext` with `userId = "local-dev-user"`
- If `Authorization` header is **present** → does nothing (allows real JWT flow)

This means all dev endpoints work without a Cognito token unless a real token is explicitly provided.

### CorsFilter

Implements both `ContainerRequestFilter` (preflight) and `ContainerResponseFilter` (all responses).

**Configuration:** `sudoku.cors.allowed-origins` (list). Default: `http://localhost:5173`.

**Preflight handling:** OPTIONS requests from allowed origins are short-circuited with 200 + CORS headers before reaching the actual handler. This avoids unnecessary Lambda invocation for preflight probes.

**Response headers added to all allowed-origin responses:**

```
Access-Control-Allow-Origin: {origin}
Access-Control-Allow-Methods: GET,POST,PATCH,OPTIONS
Access-Control-Allow-Headers: Content-Type,Accept,Authorization
```

### ApiLoggingFilter

Logs full request/response bodies when `sudoku.api.logging.enabled=true` (default: false; true in dev profile only).

**Request logging:** Reads entire entity stream into memory, then resets the stream via `ByteArrayInputStream` so the actual handler can still read the body. Without the reset, the handler would receive an empty body.

**Response logging:** Logs the entity object (pre-serialization) directly. No stream buffering needed on the response side.

## DevDatabaseInitializer

Observes `StartupEvent`, runs only in dev profile, auto-creates DynamoDB tables (SudokuGames, SudokuPlayers) on startup. Catches `ResourceInUseException` if tables already exist. Enables a fresh LocalStack instance to be used without manual table creation.

## Configuration Reference

| Property | Default | Profile Override | Purpose |
| --- | --- | --- | --- |
| `sudoku.dynamodb.players-table-name` | `SudokuPlayers` | env var | DynamoDB table name |
| `app.allowed.emails` | (empty) | `%prod`: 4 addresses | Email allowlist |
| `sudoku.cors.allowed-origins` | `http://localhost:5173` | env var | CORS allowed origins |
| `sudoku.api.logging.enabled` | `false` | `%dev`: `true` | Request/response logging |
| `quarkus.oidc.enabled` | `true` | `%dev/%it/%test`: `false` | OIDC JWT validation |
| `quarkus.http.auth.proactive` | `true` | `%dev/%it/%test`: `false` | Proactive auth enforcement |
| `quarkus.http.root-path` | `/` | all: `/api/v1` | Global API path prefix |

## Observed Design Decisions

| Decision | Chosen | Alternatives Considered | Rationale |
| --- | --- | --- | --- |
| Lazy profile creation | Create on first `/players/me` | Create in Cognito post-auth trigger | No Lambda trigger infrastructure needed; simpler; profile created only if user actually uses the app |
| Email allowlist in backend filter | `AllowedUsersFilter` with `app.allowed.emails` | Cognito groups, API Gateway resource policy | Works regardless of identity provider; easy to update without Cognito changes; visible in config |
| `@IfBuildProfile` for DevUserFilter | Compile-time exclusion | Runtime profile check | Zero production cost; dev code literally absent from production artifact |
| CorsFilter in JAX-RS layer | Custom `ContainerRequestFilter` | Quarkus built-in CORS (`quarkus.http.cors`) | More control over origin matching; allows OPTIONS short-circuit before business logic |
| `upsert` as full overwrite | DynamoDB `PutItem` | UpdateExpression on specific fields | PlayerProfile has few fields; full overwrite is simpler and correct |
| Profile update via PATCH | PATCH /players/me for displayName + avatarKey | Read-only after creation | Users need to personalise their display name and avatar across devices |
| avatarKey not server-validated | Free string; client defines valid values | Enum allowlist on backend | Icon set is UI-defined; future custom upload will use a different prefix convention |

## Technical Debt & Inconsistencies

- `PlayerServiceImpl.getOrCreateProfile()` does not update email or displayName from JWT claims on subsequent GET calls — the first-created values are frozen for email. Display name and avatar can be changed via PATCH /players/me.
- `PlayerServiceImpl.updateProfile()` uses read-modify-write (findById → merge → upsert). Two concurrent PATCHes from the same user can race; the last write wins. Acceptable for a single-user personal app but would need conditional writes at scale.
- `DynamoDbPlayerRepository.upsert()` returns the original `PlayerProfile` unchanged (not the DynamoDB-persisted version). If DynamoDB applies any transformation, the returned value would be stale — though no such transformation currently exists.
- `AllowedUsersFilter` passes anonymous requests silently. If the OIDC stack fails to populate the `SecurityIdentity` for an authenticated route, the request would slip through the allowlist check. API Gateway's JWT authorizer is the actual enforcement point; this filter is defence-in-depth.
- `ApiLoggingFilter` buffers the entire request body in memory for logging. For large payloads (e.g., image uploads), this doubles memory usage. Image upload goes through the image recognition Lambda, not this path, so current exposure is low.
- The production allowlist is hardcoded in `application.properties` rather than injected via an environment variable. Adding a new user requires a code commit. [D]

## Behavioral Quirks

- `GET /players/me` is idempotent from the caller's perspective but has a write side effect on first call (creates the profile). Repeated calls return the same data.
- In dev profile, `DevUserFilter` always uses `userId = "local-dev-user"` regardless of any request parameter. All dev game data is stored under this single user ID in LocalStack.
- The CORS filter does not reject requests from non-allowed origins — it simply does not add the CORS headers. Browsers enforce CORS; the filter's job is to signal allowance, not to block.

## Cognito User Pool Provisioning

Cognito is provisioned via Terraform. Key resources:

- `aws_cognito_user_pool` — social-only sign-in (Google); no native username/password; email schema required
- `aws_cognito_identity_provider` — Google OAuth identity provider with `authorize_scopes = "openid email profile"`
- `aws_cognito_user_pool_client` — two clients per workspace: `sudoku-web{suffix}` (public SPA, PKCE) and `sudoku-smoke-test{suffix}` (CI tests, client secret)
- `aws_cognito_user_pool_domain` — hosted UI domain `sudoku-auth{suffix}.auth.eu-west-2.amazoncognito.com`

Google credentials (`google_client_id`, `google_client_secret`) are passed as sensitive Terraform variables — never committed. In CI/CD they are injected via GitHub Actions secrets.

### RC workspace Cognito sharing

All `rc-*` workspaces share a single Cognito pool (`sudoku-rc`) owned by the `rc-shared` workspace. This avoids multiplying Google OAuth redirect URI registrations — one pool means one set of whitelisted URIs.

### Cognito callback URL tightening

Cognito `callback_urls` and `logout_urls` cannot reference the Amplify URL at `terraform apply` time (circular dependency). Baseline wildcards are applied by Terraform; a post-apply CI script tightens them to the exact Amplify URL. `ignore_changes = [callback_urls, logout_urls]` in Terraform state prevents subsequent applies from reverting. See `docs/llds/cloud-platform.md` — CORS Two-Step Pattern.

## Frontend Amplify Auth Bootstrap

### Libraries

```
@aws-amplify/auth          # Core Auth module
@aws-amplify/ui-react      # <Authenticator> wrapper component
aws-amplify                # Hub for auth events
```

### Bootstrap (main.jsx)

Configure Amplify once before React mounts:

```js
import { Amplify } from 'aws-amplify';
Amplify.configure({
  Auth: {
    Cognito: {
      userPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID,
      userPoolClientId: import.meta.env.VITE_COGNITO_CLIENT_ID,
      loginWith: { oauth: { /* hosted UI config */ } }
    }
  }
});
```

Wrap the root component with `<Authenticator>`. Unauthenticated users see the Cognito hosted UI. Once authenticated, the child receives `{ user, signOut }`.

`VITE_SKIP_AUTH=true` bypasses the Authenticator entirely — used in test environments only; never set in production.

## Token Management

After login, Cognito issues three tokens:

| Token | Use |
| --- | --- |
| **ID token** | Sent as `Authorization: Bearer <id_token>` on every authenticated API call. Contains `sub`, `email`, `name`. |
| **Access token** | Not used — Cognito User Pool access token is redundant here. |
| **Refresh token** | Managed automatically by Amplify; rotates on every use. |

Always use the **ID token** as the Bearer. Retrieve it with:

```js
import { fetchAuthSession } from 'aws-amplify/auth';
const { tokens } = await fetchAuthSession();
const idToken = tokens.idToken.toString();
```

## Route Authorizer Matrix

| Route | Authorizer |
| --- | --- |
| `GET /puzzles/generate` | None (public) |
| `POST /puzzles/validate` | None (public) |
| `POST /puzzles/hint` | None (public) |
| `POST /puzzles/candidates` | None (public) |
| `GET /health` | None (public) |
| `POST /api/v1/games` | JWT required |
| `GET /api/v1/games/{gameId}` | JWT required |
| `PATCH /api/v1/games/{gameId}` | JWT required |
| `GET /api/v1/games/current` | JWT required |
| `GET /api/v1/players/me` | JWT required |
| `PATCH /api/v1/players/me` | JWT required |
| `POST /api/v1/puzzles/import` | JWT required |

The JWT authorizer is configured at API Gateway level (`identity_sources = ["$request.header.Authorization"]`, `issuer` = Cognito pool URL, `audience` = web client ID). The Lambda never validates the JWT itself.

## Environment Variables Reference

### Frontend (VITE_*)

| Variable | Description |
| --- | --- |
| `VITE_COGNITO_USER_POOL_ID` | Cognito User Pool ID (e.g. `eu-west-2_aBcDeFgHi`) |
| `VITE_COGNITO_CLIENT_ID` | Web App Client ID |
| `VITE_COGNITO_DOMAIN` | Hosted UI domain |
| `VITE_SKIP_AUTH` | `true` in test environments only — bypasses Authenticator |

These are non-secret (safe to embed in frontend bundles). Injected by Terraform into the Amplify build environment.

### Backend (application.properties / Lambda env)

No Cognito-specific env vars needed — the backend trusts API Gateway's injected JWT claims only. OIDC validation is disabled in dev/test profiles (`quarkus.oidc.enabled=false`).

## References

- `backend/src/main/java/.../player/` (all player files)
- `backend/src/main/java/.../auth/AllowedUsersFilter.java`
- `backend/src/main/java/.../developer/DevUserFilter.java`
- `backend/src/main/java/.../developer/DevDatabaseInitializer.java`
- `backend/src/main/java/.../cors/CorsFilter.java`
- `backend/src/main/java/.../logging/ApiLoggingFilter.java`
- `backend/src/main/resources/application.properties`
- See also: `docs/llds/cloud-platform.md` (Cognito provisioning, API Gateway JWT Authorizer, IAM)
- See also: `docs/llds/react-frontend.md` (Amplify Auth bootstrap, `VITE_SKIP_AUTH`)
- Depends on: nothing (terminal leaf; Cognito auth is external)
- Depended on by: Game Lifecycle (userId from JWT), Puzzle Generation (public routes bypass auth)
