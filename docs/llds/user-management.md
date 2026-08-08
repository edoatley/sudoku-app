# User Management

**Created**: 2026-04-18
**Status**: Complete

## Context and Current State

The User Management component covers everything related to player identity: profile creation and retrieval, JWT-based authentication, email allowlisting, and the cross-cutting filters that enforce security policies on every request. It also includes the developer infrastructure that makes local testing possible without a live Cognito pool.

Files: `player/PlayerResource.java`, `player/PlayerService.java`, `player/PlayerServiceImpl.java`, `player/PlayerRepository.java`, `player/DynamoDbPlayerRepository.java`, `player/PlayerItem.java`, `player/PlayerProfile.java`, `web/filter/AllowedUsersFilter.java`, `admin/AdminOnly.java`, `admin/AdminAuthorizationFilter.java`, `admin/AdminDataResource.java`, `developer/DevUserFilter.java`, `developer/DevDatabaseInitializer.java`, `cors/CorsFilter.java`, `logging/ApiLoggingFilter.java`.

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

In **dev/IT/test** profiles, `DevIdentityAugmentor` replaces the JWT layer:

```text
              DevIdentityAugmentor (SecurityIdentityAugmentor, @IfBuildProfile dev/it/test)
              (anonymous request → inject a Firebase-shaped mock JWT resolving to userId="local-dev-user";
               disabled by sudoku.dev.mock-identity.enabled=false)
                       │
                       ▼
              AllowedUsersFilter
              (allowlist empty in dev → passes all requests)
```

## Multi-Cloud Authentication & Identity (GCP facet)

The architecture above is the **AWS** deployment. The backend also runs on **GCP** (games + player-profile slice); the edge differs but the in-app path is the same.

### Where the token is verified — in-app only on GCP

On AWS the token is validated twice: the API Gateway JWT authorizer at the edge, then Quarkus OIDC in-app. **GCP has no gateway** — the Cloud Run service is a public endpoint (`roles/run.invoker = allUsers`), so **Quarkus OIDC in-app validation is the sole gate**. Consequence: every non-public resource must carry its `@Authenticated`/role annotation — a missing annotation is an open endpoint on GCP with no edge backstop. This is a **deliberate, accepted** choice (in-app-only is the standard Cloud Run pattern for a public JWT API, and the AWS app already validates in-app regardless), compensated by a **route-coverage test** asserting every non-public route rejects an anonymous request.

### Cloud-parameterized OIDC

A single Quarkus OIDC tenant, configured per deployment by env:

| Setting | AWS (Cognito) | GCP (Identity Platform / Firebase) |
| --- | --- | --- |
| issuer / auth-server-url | Cognito pool issuer | `https://securetoken.google.com/<project_id>` |
| discovery | enabled | **disabled** — Firebase is not OIDC-discoverable |
| JWKS | from discovery | explicit: `https://www.googleapis.com/robot/v1/metadata/jwk/securetoken@system.gserviceaccount.com` |
| audience | web client id | `<project_id>` |

### Canonical userId = Google sub (hardened resolver)

`userId` is the **Google `sub`** — the federated-identity subject, stable across IdPs (HLD *Cloud-Agnostic Persistence & Auth*). `UserIdentityResolver` derives it with a **strict provider allow-list, not a permissive fallback** — any provider it does not recognise is rejected (401), so an unexpected token shape never reaches a repository:

| `firebase.sign_in_provider` | Resolved `userId` |
| --- | --- |
| `google.com` | `firebase.identities["google.com"][0]` — the **raw** Google `sub` (matches AWS's future re-key value) |
| `password` | **`firebase:<uid>`** — namespaced; for the pre-provisioned CI smoke-test user only |
| anything else (anonymous, phone, unknown) | **reject (401)** |

The two namespaces are disjoint by construction: Google users key on the raw `sub`, the `password` fallback on a `firebase:`-prefixed `uid`, so a `password` token can never resolve onto a Google user's data. This fallback is safe **only in combination with** the Identity Platform hardening (Google + password providers only, **self-signup disabled**, no anonymous — see runbook §4/§5) and the email allowlist below; together they mean the `password` path can only ever be the allowlisted, admin-provisioned test user. This matches the AWS posture — the Cognito smoke-test client is likewise a controlled native user gated by the same allowlist.

On **AWS** the resolver returns `jwt.getSubject()` (the Cognito subject) today; the deferred re-key switches it to the Google `sub` in the Cognito `identities` claim.

**Account-linking is not supported.** Resolution keys on `firebase.sign_in_provider` for the *current* sign-in, so each account must use exactly one provider — Google for real users, `password` for the test user. A single account linking both providers (which would yield two `userId`s for one human) is out of scope and not enabled in Identity Platform.

**Dev/test — mock token, no resolver carve-out.** In dev/it/test (OIDC disabled), `DevUserFilter` injects a **Firebase-shaped mock `JsonWebToken`** — a fixed `google.com` identity for `local-dev-user` — so `UserIdentityResolver` runs its real logic unchanged (the strict allow-list is never bypassed). Backend tests generate `google.com`, `password`, and unknown-provider tokens with a JWT builder (`io.smallrye.jwt.build.Jwt`) to cover every resolver branch, including the reject path.

**Authorization gate.** A valid token is authentication only. `AllowedUsersFilter` requires the token's `email` claim to be on `app.allowed.emails`. It additionally requires `email_verified = true` **only for Firebase (GCP) tokens** (detected by the `firebase` claim), where in-app validation is the sole gate — Firebase reports `email_verified` truthfully and the `password` test user is marked verified via the Admin API, closing the gap where an unverified, attacker-chosen email could match the allowlist. It is **not** required for Cognito (AWS) tokens: Cognito sets `email_verified=false` for every external-provider (Google-federated) user even though the email came from Google, and the request is already gated by the API Gateway Cognito authorizer with self-signup disabled — so enforcing it there would lock out legitimate users with no security gain. Email / display-name extraction (`email`, `name`) is otherwise unchanged, so `PlayerResource` claim extraction works on both clouds.

### CORS

With no gateway on GCP, **CORS is handled entirely in-app** (`CorsFilter`, driven by `CORS_ALLOWED_ORIGINS`). The Firebase Hosting origin (`https://sudoku-app-eo.web.app`, later the custom domain) calls the Cloud Run origin cross-origin, so it must be in the allowed list. Because in-app validation is the sole gate, preflight `OPTIONS` (which carries no `Authorization` header) must be answered by `CorsFilter` **before** `@Authenticated` runs — otherwise every cross-origin preflight would 401. This ordering is required, not incidental.

### Player persistence

The profile persists through the existing `PlayerRepository` port. GCP adds `FirestorePlayerRepository` — a `players` collection keyed on the Google-`sub` `userId` — selected at build time (`sudoku.persistence=firestore`). Lazy-creation and PATCH-update behaviour are unchanged; only the adapter differs.

### Deferred: admin authorization on GCP

Identity Platform has no group concept, so the `administrators`-group check (see *Admin Authorization*) has no GCP equivalent yet. `/admin/*` is **not** in this slice, so it is deferred; when admin lands on GCP it moves to a custom claim (Firebase Admin SDK) or a configured email allowlist.

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
PATCH /players/me { displayName?, avatarKey?, aiCoachEnabled? }
  → playerService.updateProfile(userId, request)
      → validate: all three null → InvalidPlayerUpdateException (400)
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
| PATCH | `/players/me` | Required | `PlayerUpdateRequest` | `PlayerProfile` | All fields optional; at least one required |

`PlayerUpdateRequest` is a record with three optional fields:
- `displayName` — string; trimmed; 1–50 chars when provided
- `avatarKey` — string; any non-null value accepted (client-defined icon name)
- `aiCoachEnabled` — boolean; enables or disables the AI coach feature for this player

All null → 400. No-op PATCH (empty payload) → 400.

## DynamoDB Schema

Table: `SudokuPlayers{suffix}` (name injected via `sudoku.dynamodb.players-table-name`)

| Attribute | DynamoDB Type | Key Role | Notes |
| --- | --- | --- | --- |
| `userId` | String | Partition Key | Canonical user id — Cognito `sub` on AWS today (→ Google `sub` after the deferred re-key); the Google `sub` on GCP's Firestore `players` collection. See *Multi-Cloud Authentication & Identity* |
| `email` | String | — | From Cognito JWT; read-only after creation |
| `displayName` | String | — | From Cognito JWT name claim; updatable via PATCH |
| `avatarKey` | String | — | Client-defined icon name; empty string stored as null; updatable via PATCH |
| `createdAt` | String | — | ISO-8601 UTC |
| `updatedAt` | String | — | ISO-8601 UTC; updated on every PATCH |
| `aiCoachEnabled` | Boolean | — | Defaults to `true` on creation; updatable via PATCH |
| `coachTokensUsedThisMonth` | Number | — | Cumulative Bedrock tokens used in `coachTokenMonth`; reset to 0 when month changes |
| `coachTokenMonth` | String | — | Format `YYYY-MM`; used to detect month rollover for token counter reset |

No sort key — one profile per user. `upsert` uses DynamoDB `PutItem` (full overwrite) for profile updates; `incrementCoachTokens` uses `UpdateItem` with `ADD` expression for atomic token counting.

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

## Admin Authorization

`/admin/*` endpoints (currently the data-browser: `GET /admin/data/games`, `GET /admin/data/players`) are reachable in production, restricted to members of a Cognito group.

**`@AdminOnly`** — a JAX-RS `@NameBinding` annotation. Only resources/methods annotated `@AdminOnly` invoke the filter below; unlike `AllowedUsersFilter` (which is a global `@Provider`), this is scoped per-endpoint.

**`AdminAuthorizationFilter`** — `@Provider @AdminOnly`, mirrors `AllowedUsersFilter`'s structure:

```text
filter(request):
  if identity.isAnonymous() → pass (dev/it/test skip; see rationale below)
  groups = extractGroups(identity)   # cognito:groups claim: JsonWebToken.getClaim(...) → JsonArray,
                                      # falling back to identity.getAttribute("cognito:groups")
  if adminGroup not in groups:
    abort(403, {"error": "Access denied"})
```

**Configuration:** `app.admin.group` (default `administrators`). No per-profile override — the `isAnonymous()` skip already covers dev/it/test.

**Why `isAnonymous()`-skip is safe:** In dev/it/test profiles, OIDC is disabled (`%dev.quarkus.oidc.enabled=false`, etc.), so `SecurityIdentity` is always anonymous and the local data browser must keep working without a token — same reasoning `AllowedUsersFilter` already relies on. In production, `/admin/*` routes are JWT-protected at API Gateway (see `docs/llds/cloud-platform.md`), so a tokenless request is rejected with 401 before the Lambda is invoked; the filter only ever observes authenticated identities there.

**Cognito groups:** Membership in the `administrators` Cognito group injects a `cognito:groups` claim into ID/access tokens automatically — no Lambda trigger required. API Gateway's JWT authorizer validates issuer + audience only; the group check is enforced entirely in this filter.

**`AdminDataResource`** (`@Path("/admin/data") @AdminOnly`) — full DynamoDB `scan()` over the Games and Players tables (`GET /games`, `GET /players`), returned as `DataListResponse<GameState>` / `DataListResponse<PlayerProfile>`. Formerly `DevDataResource` under `/dev/data`, unauthenticated and shipped to production by accident (see `docs/planning/infra-review.md` finding H1) — moved here and gated so the same functionality (needed for admin visibility into live data) requires both a valid JWT and `administrators` group membership.

**Residual risk:** the `dynamodb:Scan` IAM grants this resource depends on live on the shared main-app Lambda execution role (`docs/llds/cloud-platform.md` — IAM), not an isolated admin role. A bug in `AdminAuthorizationFilter` is the only thing standing between an authenticated non-admin user and a full table scan. Fully isolating this would require a separate admin Lambda — out of scope for now.

## Cross-Cutting Request Filters

### DevIdentityAugmentor (`@IfBuildProfile(anyOf = {"dev", "it", "test"})`)

Compiled out of production builds entirely. Replaces the former `DevUserFilter` (which set only the JAX-RS `SecurityContext`) with a `SecurityIdentityAugmentor` that populates the Quarkus `SecurityIdentity`, so `UserIdentityResolver` and the request filters run their production logic. When active:

- An **anonymous** request (OIDC is disabled in these profiles) → augmented with a Firebase-shaped mock `JsonWebToken` (a `google.com` identity for `local-dev-user`, carrying the `administrators` group so the local `/admin` data browser works) that resolves to `userId = "local-dev-user"`
- Set `sudoku.dev.mock-identity.enabled=false` → requests stay anonymous (used by the route-coverage test to prove protected routes reject anonymous callers)

This means all dev endpoints work without a real token unless the mock is explicitly disabled.

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
| Admin authorization via Cognito group | `cognito:groups` claim checked in `AdminAuthorizationFilter` (name-bound `@AdminOnly`) | API Gateway resource policy; separate admin-only API Gateway/Lambda; IAM-based per-user policy | Cognito groups need no new infrastructure (no Lambda trigger, no separate API); consistent with the existing JWT-claim-based `AllowedUsersFilter` pattern; group membership changes without a deploy |

## Technical Debt & Inconsistencies

- `PlayerServiceImpl.getOrCreateProfile()` does not update email or displayName from JWT claims on subsequent GET calls — the first-created values are frozen for email. Display name and avatar can be changed via PATCH /players/me.
- `PlayerServiceImpl.updateProfile()` uses read-modify-write (findById → merge → upsert). Two concurrent PATCHes from the same user can race; the last write wins. Acceptable for a single-user personal app but would need conditional writes at scale.
- `DynamoDbPlayerRepository.upsert()` returns the original `PlayerProfile` unchanged (not the DynamoDB-persisted version). If DynamoDB applies any transformation, the returned value would be stale — though no such transformation currently exists.
- `AllowedUsersFilter` passes anonymous requests silently. If the OIDC stack fails to populate the `SecurityIdentity` for an authenticated route, the request would slip through the allowlist check. API Gateway's JWT authorizer is the actual enforcement point; this filter is defence-in-depth.
- `ApiLoggingFilter` buffers the entire request body in memory for logging. For large payloads (e.g., image uploads), this doubles memory usage. Image upload goes through the image recognition Lambda, not this path, so current exposure is low.
- The production allowlist is hardcoded in `application.properties` rather than injected via an environment variable. Adding a new user requires a code commit. [D]
- `/admin/*` endpoints are gated by both `AllowedUsersFilter` (global) and `AdminAuthorizationFilter` (name-bound `@AdminOnly`) — a caller must be on the email allowlist *and* in the `administrators` Cognito group. This is intentional layering (matches the existing defence-in-depth pattern) rather than a bug, but means removing someone from the email allowlist alone is sufficient to revoke their admin access without touching the Cognito group.

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
| `POST /api/v1/ai/coach` | JWT required |
| `POST /api/v1/ai/image-to-puzzle` | JWT required |
| `GET /api/v1/admin/data/games` | JWT required + `administrators` Cognito group |
| `GET /api/v1/admin/data/players` | JWT required + `administrators` Cognito group |

The JWT authorizer is configured at API Gateway level (`identity_sources = ["$request.header.Authorization"]`, `issuer` = Cognito pool URL, `audience` = both the web client ID and the smoke-test client ID — CI authenticates via the latter). The Lambda never validates the JWT itself.

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
- `backend/src/main/java/.../web/filter/AllowedUsersFilter.java`
- `backend/src/main/java/.../admin/AdminOnly.java`, `AdminAuthorizationFilter.java`, `AdminDataResource.java`
- `backend/src/main/java/.../developer/DevUserFilter.java`
- `backend/src/main/java/.../developer/DevDatabaseInitializer.java`
- `backend/src/main/java/.../cors/CorsFilter.java`
- `backend/src/main/java/.../logging/ApiLoggingFilter.java`
- `backend/src/main/resources/application.properties`
- See also: `docs/llds/cloud-platform.md` (Cognito provisioning, API Gateway JWT Authorizer, IAM)
- See also: `docs/llds/react-frontend.md` (Amplify Auth bootstrap, `VITE_SKIP_AUTH`)
- Depends on: nothing (terminal leaf; Cognito auth is external)
- Depended on by: Game Lifecycle (userId from JWT), Puzzle Generation (public routes bypass auth)
