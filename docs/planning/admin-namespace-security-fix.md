# Implementation Plan: Admin namespace for `/dev/data/*` (fixes public PII exposure)

**Created**: 2026-07-08
**Branch**: `rc-admin-endpoints` (renamed from `security/admin-namespace-dev-endpoints` so it deploys to the `rc-shared`/sudoku-beta environment for manual verification)
**Status**: Implemented — pending user's pre-push run and manual RC verification
**Severity**: HIGH — closes a live, unauthenticated production PII leak.

---

## 1. Why this change exists

On 2026-07-08 an unauthenticated request to the production API returned the entire users table:

```
GET https://7xv70er20f.execute-api.eu-west-2.amazonaws.com/api/v1/dev/data/players
→ HTTP 200, full SudokuPlayers table (every user's displayName, email, userId)
```

`GET /api/v1/dev/data/games` behaves the same. See `docs/planning/infra-review.md` finding **H1** for the confirmed evidence.

**Two failures combine to cause it:**

1. **Backend**: `DevDataResource` (`@Path("/dev/data")`) and `DevResource` (`@Path("/dev")`) are compiled into the production Lambda bundle. Unlike `DevUserFilter` (which has `@IfBuildProfile(anyOf={"dev","it","test"})`), they have **no build-profile guard**, so they ship to prod. `DevDataResource` runs full DynamoDB `scan()` over Games and Players.
2. **Infra**: the API Gateway `$default` route (`infra/api_gateway.tf`, `local.base_routes["$default"]`) is **public** (no `authorization_type`) and proxies every unmatched path to the Java Lambda. With `quarkus.http.root-path=/api/v1`, that includes `/api/v1/dev/*`. The `CKV_AWS_309` checkov-skip comment claiming "$default catches only public routes" is therefore wrong.

## 2. Intended outcome

- The data-browser endpoints become a first-class **`/admin/data/*`** namespace, **authenticated + authorized to a Cognito `administrators` group**, and remain usable **in production by admins**.
- The `hint-demo` dev aid (`GET /dev/hint-demo`, no user data) is **compiled out of production entirely** (it has no admin use case).
- In the production UI, the data browser is shown **only to users in the admin group** (today it is hidden by `VITE_DEV_TOOLS=false` and never reachable in prod).
- Old paths `/api/v1/dev/data/*` and `/api/v1/dev/hint-demo` return **404** in prod (handlers gone / compiled out).

## 3. Key facts about the current code (verified — trust these)

- **Auth model**: Quarkus OIDC + JAX-RS. **No `@RolesAllowed` anywhere in the codebase.** User identity comes from `SecurityContext.getUserPrincipal().getName()` (userId = Cognito `sub`) in resources, or `@Inject SecurityIdentity` in filters. JWT claims: `identity.getPrincipal() instanceof JsonWebToken jwt → jwt.getClaim("...")`, with `identity.getAttribute("...")` as a fallback.
- **The pattern to copy**: `backend/src/main/java/com/sudoku/web/filter/AllowedUsersFilter.java` — a `@Provider ContainerRequestFilter` that injects `SecurityIdentity`, reads a config-driven value, **returns early when `identity.isAnonymous()`**, and `abortWith(403, {"error":"Access denied"})` otherwise. Its test `backend/src/test/java/com/sudoku/web/filter/AllowedUsersFilterTest.java` shows how to mock `SecurityIdentity` + `JsonWebToken`.
- **Why `isAnonymous()`-skip is safe**: in `dev`/`it`/`test` profiles OIDC is disabled (`%dev.quarkus.oidc.enabled=false`, etc.) so `SecurityIdentity` is anonymous — the local data browser must keep working without a token. In **prod**, the new `/admin/*` routes are JWT-protected at the gateway, so an anonymous (tokenless) request is rejected with 401 **before** reaching the Lambda; the filter only ever sees authenticated identities there. This is exactly how `AllowedUsersFilter` already reasons.
- **Cognito groups**: none exist yet. Cognito **automatically** injects a `cognito:groups` claim (a JSON array of group names) into ID and access tokens for any user who is a group member — **no pre/post-authentication Lambda trigger is required**. The API Gateway JWT authorizer validates issuer + audience only; **group enforcement must happen in the Lambda** (this filter).
- **Frontend**: `ui/src/api/sudokuApi.js` — `getDevData(entity)` calls `${API_URL}/dev/data/${entity}` via `apiFetch(label, url)` with **no auth** (the 4th `authenticated` arg defaults false). `apiFetch(label, url, options, authenticated, nullStatuses)` attaches `Authorization: Bearer <idToken>` when `authenticated === true`; token via `getIdToken()` → `fetchAuthSession()` (aws-amplify/auth). The data browser dialog is `ui/src/components/DevDataDialog.jsx`, gated in `ui/src/App.jsx` by `const DEV_TOOLS = import.meta.env.VITE_DEV_TOOLS === 'true'`. A `403` throws `ForbiddenError`, already caught → "Access Denied" dialog. There is no role/group concept in the UI yet.
- **IAM** (`infra/iam.tf`): `dynamodb:Scan` is granted on Games (`sudoku_dynamodb`), Players (`sudoku_players_dynamodb`), and Leaderboard (`sudoku_leaderboard_dynamodb`). Games/Players Scan are used **only** by the data browser; Leaderboard Scan is used by `DynamoDbLeaderboardRepository.getLeaderboard()`.

## 4. Implementation steps

Follow the project's mandatory **linked-intent-dev** workflow (HLD → LLD → EARS → Tests → Code). Consult the `linked-intent-dev` skill first. This is a new feature → full workflow. Annotate new code and tests with `// @spec ...`.

### 4.1 Backend — new package `backend/src/main/java/com/sudoku/admin/`

**`AdminOnly.java`** — JAX-RS name-binding annotation:
```java
package com.sudoku.admin;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@NameBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface AdminOnly {}
```

**`AdminAuthorizationFilter.java`** — mirror `AllowedUsersFilter` closely:
- Class annotations: `@Provider @AdminOnly` (name-binding means it runs **only** on resources/methods annotated `@AdminOnly`, not globally).
- `@Inject SecurityIdentity identity;`
- Admin group name from `@ConfigProperty(name = "app.admin.group") String adminGroup` (default `administrators`; see 4.2).
- `filter(ContainerRequestContext ctx)`:
  1. `if (identity.isAnonymous()) return;` (dev/test skip; safe in prod per §3).
  2. Extract group names into a `Set<String>`:
     - If `identity.getPrincipal() instanceof JsonWebToken jwt`, read `Object claim = jwt.getClaim("cognito:groups")`. Cognito serialises this as a `jakarta.json.JsonArray`; also tolerate a `Collection<?>` / `String[]`. Map each element's string value into the set.
     - Fallback: `identity.getAttribute("cognito:groups")` (may be a `Collection`).
  3. `if (!groups.contains(adminGroup)) ctx.abortWith(Response.status(FORBIDDEN).type(APPLICATION_JSON).entity("{\"error\":\"Access denied\"}").build());`
  - Null / absent groups → not present → 403. Keep the helper (`extractGroups`) private, javadoc'd, matching `AllowedUsersFilter`'s style.

**`AdminDataResource.java`** — move the data browser out of `developer/`:
- `@ApplicationScoped @Path("/admin/data") @Produces(MediaType.APPLICATION_JSON) @AdminOnly`.
- Move the two handlers **verbatim** from `DevDataResource` (`GET /games`, `GET /players`), plus the `@Inject DynamoDbEnhancedClient`, the `@ConfigProperty` table-name fields, the `@PostConstruct init()` wiring, and the `DataListResponse<GameState>` / `DataListResponse<PlayerProfile>` mapping. No logic changes.
- Update the class javadoc: it is now an **admin-only** endpoint reachable in production by members of the `administrators` group (delete the stale "only reachable locally / not registered in any production route" comment).

**`DevDataResource.java`** — **delete** (fully replaced by `AdminDataResource`).

**`DevResource.java`** (hint-demo) — add `@IfBuildProfile(anyOf = {"dev", "it", "test"})` at class level (mirrors `DevUserFilter`) so it is compiled out of the prod bundle. No other change.

### 4.2 Backend config — `backend/src/main/resources/application.properties`
Add near the email-allowlist block:
```properties
# Cognito group whose members may reach /admin/* endpoints.
app.admin.group=administrators
```
No per-profile override needed — dev/it/test are covered by the `isAnonymous()` skip.

### 4.3 Frontend — `ui/src/`

**`api/sudokuApi.js`**:
- Rename `getDevData(entity)` → `getAdminData(entity)`; change the URL to `${API_URL}/admin/data/${encodeURIComponent(entity)}` and pass `authenticated=true` (the 4th positional arg of `apiFetch`). Keep the `MOCK_API` early-return.
- Add an admin-detection helper, e.g.:
  ```js
  export async function getAdminGroups() {
    const { fetchAuthSession } = await import('aws-amplify/auth');
    const session = await fetchAuthSession();
    const groups = session.tokens?.idToken?.payload?.['cognito:groups'];
    return Array.isArray(groups) ? groups : [];
  }
  export async function isAdmin() {
    return (await getAdminGroups()).includes('administrators');
  }
  ```
- Leave `getDemoGrid()` (`/dev/hint-demo`) **unchanged** — it is dev-only now (compiled out of prod) and unauthenticated in local dev.

**`App.jsx`**:
- Resolve admin status once on load (e.g. a `const [admin, setAdmin] = useState(false)` populated from `isAdmin()` in an effect, guarded for logged-out users).
- Gate the data browser on `DEV_TOOLS || admin`: pass `onDevData={(DEV_TOOLS || admin) ? () => setDevDataOpen(true) : null}` and render `{(DEV_TOOLS || admin) && <DevDataDialog .../>}`.
- Keep `loadDemoGame` / hint-demo strictly `DEV_TOOLS`-only (it no longer exists in prod — do **not** tie it to `admin`).
- Update `DevDataDialog.jsx` to call `getAdminData` instead of `getDevData`.
- Optional polish: label the menu item "Admin → Data Browser" when shown via `admin` rather than `DEV_TOOLS`.

### 4.4 Infra — `infra/`

**`cognito.tf`** (owned pool, default + non-rc):
```hcl
resource "aws_cognito_user_group" "admin" {
  count        = local.is_rc ? 0 : 1
  name         = "administrators"
  user_pool_id = aws_cognito_user_pool.main[0].id
  description  = "Members may reach /admin/* endpoints"
}
```

**`cognito-rc-shared.tf`** (shared rc pool):
```hcl
resource "aws_cognito_user_group" "rc_admin" {
  count        = terraform.workspace == "rc-shared" ? 1 : 0
  name         = "administrators"
  user_pool_id = aws_cognito_user_pool.rc_shared[0].id
  description  = "Members may reach /admin/* endpoints"
}
```

**Group membership — do NOT try to manage the human admin in Terraform.** The production admin signs in via Google, so their Cognito username is a federated id (`google_<sub>`), unknown until first login — `aws_cognito_user_in_group` cannot reference it at apply time. Instead, document a one-time step (same spirit as the existing manual DNS-delegation step), run after the admin has logged in once:
```bash
# Find the federated username, then add to the group:
AWS_PROFILE=sandbox aws cognito-idp list-users \
  --user-pool-id <POOL_ID> --filter 'email = "edoatley@gmail.com"' \
  --query 'Users[0].Username' --output text
AWS_PROFILE=sandbox aws cognito-idp admin-add-user-to-group \
  --user-pool-id <POOL_ID> --username <FEDERATED_USERNAME> --group-name administrators
```
Consider adding `scripts/infra/add-admin.sh <email>` wrapping this. Do **not** make the native smoke-test user an admin.

**`api_gateway.tf`** — add to `local.base_routes` (mirror `"POST /api/v1/games"`):
```hcl
"GET /api/v1/admin/data/games" = {
  authorization_type = "JWT"
  authorizer_key     = "cognito_jwt"
  integration        = local._lambda_integration
}
"GET /api/v1/admin/data/players" = {
  authorization_type = "JWT"
  authorizer_key     = "cognito_jwt"
  integration        = local._lambda_integration
}
```
Update the `CKV_AWS_309` skip comment: `/dev/data` is no longer public; admin data is on explicit JWT routes.

**`iam.tf` — read this carefully; it corrects an earlier recommendation.** Because the data browser now stays in production for admins, the Games and Players `dynamodb:Scan` grants **must remain** — they are what `AdminDataResource` uses. This **supersedes** finding H1's "drop Scan" sub-point. Do **not** remove them. Change only the two policy `description` strings to reference the "admin data browser" so intent is clear. (Leaderboard Scan is unrelated and stays.) Residual risk to record in the LLD: these Scan grants live on the shared main-app Lambda role; fully isolating them would require a separate admin Lambda — out of scope here.

### 4.5 Linked-intent docs (mandatory)
- **LLD** `docs/llds/user-management.md`: new "Admin Authorization" section (group model, filter, `app.admin.group`, `isAnonymous` dev-skip rationale, the residual Scan-grant note). `docs/llds/cloud-platform.md`: add the two `/admin/data/*` rows to the JWT-protected route table.
- **EARS** `docs/specs/user-management-specs.md`: new "Admin Authorization" section, e.g.
  - `UM-BE-060`: While a request carries a JWT whose `cognito:groups` claim contains `administrators`, the system shall allow access to `/admin/*` endpoints.
  - `UM-BE-061`: While an authenticated request's `cognito:groups` claim does not contain `administrators`, the system shall reject `/admin/*` requests with HTTP 403 and a JSON error body.
  - `UM-BE-062`: Where the dev, it, or test build profile is active (anonymous identity), the system shall not apply the admin group check.
  - `UM-BE-063`: The DevResource (hint-demo) shall be compiled out of the production build artifact.
  - Add a gateway-route spec for `GET /api/v1/admin/data/*` in `docs/specs/cloud-platform-specs.md`.
- **Arrows**: update `docs/arrows/user-management.md`, `docs/arrows/cloud-platform.md`, and `docs/arrows/index.yaml`.

### 4.6 Tests
- **Backend** `backend/src/test/java/com/sudoku/admin/`:
  - `AdminAuthorizationFilterTest` (mirror `AllowedUsersFilterTest`): member of `administrators` → passes; authenticated non-member → 403; empty/absent `cognito:groups` → 403; anonymous → passes.
  - Move any existing `DevDataResourceTest` → `AdminDataResourceTest` (adjust package/path; behaviour unchanged).
- **Frontend** (`ui/src/**/__tests__` / Vitest): `getAdminData` sends the Bearer token and calls `/admin/data/*`; `isAdmin` parses `cognito:groups` correctly (member, non-member, missing claim); data browser renders when `admin` is true.

## 5. Verification

- **Local**: `bash scripts/local/local-dev.sh`, open Developer → Data Browser — still loads (anonymous-skip in dev).
- **Unit/validate**:
  - `cd backend && ./mvnw test`
  - `cd ui && npm run lint && npm test`
  - `cd infra && terraform fmt -check -recursive && terraform init -backend=false && terraform validate`
- **Pre-push (MANDATORY, per CLAUDE.md)**: `bash scripts/local/local-alltests.sh` — must pass before any push.
- **Post-deploy production probes** (sandbox AWS profile; API base from `terraform output api_gateway_url` or `aws apigatewayv2 get-apis`):
  - `curl -s -o /dev/null -w '%{http_code}' <base>/api/v1/admin/data/players` → **401** (no token).
  - With a non-admin user's ID token → **403**.
  - With an admin (in `administrators`) ID token → **200**.
  - `curl <base>/api/v1/dev/data/players` and `<base>/api/v1/dev/hint-demo` → **404** (leak closed / compiled out). This re-runs the exact request that proved the vulnerability.

## 6. Scope / guardrails

- **Do NOT** remove the Games/Players `dynamodb:Scan` IAM grants (see §4.4).
- **Do NOT** tie hint-demo visibility to admin status — it is compiled out of prod and stays `DEV_TOOLS`-gated.
- Keep changes surgical (project Rule 3): match `AllowedUsersFilter`'s style; don't refactor adjacent code.
- The pre-existing unstaged docs on this branch (`docs/architecture.md`, `docs/planning/infra-review.md`) are intentional and should be committed alongside or before this work — do not discard them.
