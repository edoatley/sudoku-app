# User Authentication Guide

This document defines the authentication architecture and implementation principles for the Sudoku app.
All generated code that touches auth, identity, or user data must follow these guidelines.

## Overview

Authentication is handled by **Amazon Cognito**. The frontend uses the AWS Amplify Auth library
(`@aws-amplify/auth` + `@aws-amplify/ui-react`) which provides pre-built login/signup UI components.
The backend never manages passwords or sessions — it trusts JWTs issued by Cognito and validated by
API Gateway before the Lambda is ever invoked.

---

## 1. Identity Provider: Amazon Cognito User Pool

- Provision a **Cognito User Pool** via Terraform (`aws_cognito_user_pool`).
- Enable **social login** (federated identity) via `aws_cognito_identity_provider`.
- Provision an **App Client** (`aws_cognito_user_pool_client`) with the `code` OAuth flow and
  `Authorization Code Grant`. Never use the implicit grant.
- Provision a **Cognito Domain** (`aws_cognito_user_pool_domain`) for the hosted UI.
- Allowed callback / logout URLs must reference the exact Amplify URL — use the same post-apply
  tightening pattern used for API Gateway CORS (see `docs/infrastructure.md`).

### How social provider credentials work

Cognito stores the social provider's OAuth client ID and secret **inside the User Pool** — the
backend Lambda never needs to know them, and there is no secrets management layer needed at runtime.

However, you must supply the credentials **at provisioning time** (i.e. to Terraform). Pass them
as sensitive Terraform variables (`sensitive = true`) and never commit them to the repository.
They are a one-time setup step obtained from each provider's developer console.

```hcl
variable "google_client_id"     { sensitive = true }
variable "google_client_secret" { sensitive = true }
```

In CI/CD, inject credentials via GitHub Actions secrets (not in the workflow file itself).

### Supported social providers

This app supports **Google** — a natively supported Cognito provider type with no extra
infrastructure required.

The sensitive variables required are:

| Provider | Sensitive variables |
|----------|---------------------|
| Google | `google_client_id`, `google_client_secret` |

### Terraform skeleton for Google

```hcl
resource "aws_cognito_identity_provider" "google" {
  user_pool_id  = aws_cognito_user_pool.main.id
  provider_name = "Google"
  provider_type = "Google"

  provider_details = {
    client_id                     = var.google_client_id
    client_secret                 = var.google_client_secret
    authorize_scopes              = "openid email profile"
    attributes_url                = "https://people.googleapis.com/v1/people/me?personFields="
    attributes_url_add_attributes = "true"
    authorize_url                 = "https://accounts.google.com/o/oauth2/v2/auth"
    oidc_issuer                   = "https://accounts.google.com"
    token_request_method          = "POST"
    token_url                     = "https://oauth2.googleapis.com/token"
  }

  attribute_mapping = {
    email    = "email"
    name     = "name"
    username = "sub"
  }
}
```

The endpoint URLs are Google/AWS constants — only `client_id` and `client_secret` are sensitive.

---

## 2. Frontend Authentication Flow

### Library

```
@aws-amplify/auth          # Core Auth module
@aws-amplify/ui-react      # <Authenticator> wrapper component
aws-amplify                # Hub for auth events
```

### Bootstrap

Configure Amplify once in `main.jsx` before React mounts:

```js
import { Amplify } from 'aws-amplify';
Amplify.configure({
  Auth: {
    Cognito: {
      userPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID,
      userPoolClientId: import.meta.env.VITE_COGNITO_CLIENT_ID,
      loginWith: { oauth: { ... } }   // hosted UI config
    }
  }
});
```

### Wrap the app

Wrap the root component with `<Authenticator>`. Unauthenticated users see the Cognito hosted UI
(or the Amplify-styled login form). Once authenticated, the child component receives `{ user, signOut }`.

### Tokens

After login, Cognito issues three tokens:

| Token | Use |
|-------|-----|
| **ID token** | Sent as `Authorization: Bearer <id_token>` on every API call. Contains `sub`, `email`, `name`. |
| **Access token** | Not used by this app (Cognito user pool access token). |
| **Refresh token** | Managed automatically by Amplify. Rotate on every use. |

Always use the **ID token** (not the access token) as the Bearer token. Retrieve it with:

```js
import { fetchAuthSession } from 'aws-amplify/auth';
const { tokens } = await fetchAuthSession();
const idToken = tokens.idToken.toString();
```

### API calls

Attach the token to every authenticated API call:

```js
const { tokens } = await fetchAuthSession();
const headers = {
  'Content-Type': 'application/json',
  'Authorization': `Bearer ${tokens.idToken.toString()}`
};
```

---

## 3. API Gateway JWT Authorizer

- Attach a **JWT Authorizer** (`aws_apigatewayv2_authorizer`) to every `/games/*` route.
- Set `identity_sources = ["$request.header.Authorization"]`.
- Set `issuer` to the Cognito User Pool issuer URL and `audience` to the App Client ID.
- Routes under `/puzzles/*` remain **public** (no authorizer) — guests can generate and play
  one-off puzzles without logging in. Games (saved state) require auth.

### Route authorizer matrix

| Route | Authorizer |
|-------|-----------|
| `GET /puzzles/generate` | None (public) |
| `POST /puzzles/validate` | None (public) |
| `POST /puzzles/hint` | None (public) |
| `POST /puzzles/candidates` | None (public) |
| `POST /games` | JWT required |
| `GET /games/{gameId}` | JWT required |
| `PATCH /games/{gameId}` | JWT required |
| `GET /players/me` | JWT required |

---

## 4. Backend Identity Extraction

API Gateway injects the validated JWT claims into the Lambda request context when a JWT Authorizer
is used. The `sub` claim is the stable, unique user identifier — **always use `sub` as the userId**,
never email (which can change).

In Quarkus / JAX-RS, extract the principal from the request context:

```java
@Context
SecurityContext securityContext;

String userId = securityContext.getUserPrincipal().getName(); // = JWT sub claim
```

Or inject via `@HeaderParam("X-Amzn-Oidc-Identity")` if using ALB (not applicable here).

The canonical approach with API Gateway HTTP API + JWT Authorizer is to read the injected context:

```java
// In a @RequestScoped CDI bean or directly in the resource:
@Inject
io.quarkus.security.identity.SecurityIdentity identity;

String userId = identity.getPrincipal().getName();
```

---

## 5. DynamoDB Data Isolation (IDOR Prevention)

### Key design

The `SudokuGames` table must use a **composite primary key**:

| Key | Attribute | Type | Purpose |
|-----|-----------|------|---------|
| Partition key | `userId` | String | Cognito `sub` — scopes all queries to the owner |
| Sort key | `gameId` | String | UUID per game |

This prevents Insecure Direct Object Reference (IDOR): a query for `gameId` also requires the
matching `userId`, so User A can never retrieve User B's game even if they guess the `gameId`.

### Lambda query pattern

All DynamoDB operations on `SudokuGames` must supply **both** `userId` and `gameId`:

```java
// Load game — userId injected from JWT context, never from the request body/path alone
Key key = Key.builder()
    .partitionValue(userId)   // from Cognito JWT sub
    .sortValue(gameId)        // from path parameter
    .build();
GameItem item = table.getItem(key);
```

---

## 6. Player Profile Table

A separate `SudokuPlayers` table stores the player's basic profile. This is created on first
authenticated request (lazy registration pattern).

### Table design

| Attribute | Type | Notes |
|-----------|------|-------|
| `userId` (PK) | String | Cognito `sub` |
| `email` | String | From JWT `email` claim |
| `displayName` | String | From JWT `name` or `cognito:username` claim |
| `createdAt` | String | ISO-8601 timestamp, set once on creation |
| `updatedAt` | String | ISO-8601 timestamp, updated on profile changes |

### Endpoint

`GET /players/me` — returns the authenticated user's profile, creating it if it doesn't exist.

The Lambda extracts `email` and `displayName` from the JWT claims passed through by API Gateway —
the backend never needs to call Cognito directly for this data.

---

## 7. IAM: Lambda Least Privilege (Post-Auth Additions)

When Cognito is added, extend the Lambda execution role policy to cover the new `SudokuPlayers` table:

```hcl
# Allowed on SudokuGames
dynamodb:GetItem, dynamodb:PutItem, dynamodb:UpdateItem, dynamodb:Query

# Allowed on SudokuPlayers
dynamodb:GetItem, dynamodb:PutItem, dynamodb:UpdateItem
```

The Lambda role must **not** be granted any `cognito-idp:*` permissions — the backend trusts the
JWT claims injected by API Gateway and never calls the Cognito API directly.

---

## 8. Rate Limiting

Keep the existing API Gateway throttle settings. Optionally tighten `/puzzles/generate` further
(it is the most compute-intensive endpoint and is public, making it a DoS target):

```hcl
# Route-level override on POST /games and puzzle endpoints
throttling_rate_limit  = 5   # req/s per route (tighter than global 25)
throttling_burst_limit = 10
```

---

## 9. Environment Variables

### Frontend (Vite)

| Variable | Description |
|----------|-------------|
| `VITE_COGNITO_USER_POOL_ID` | Cognito User Pool ID (e.g. `eu-west-2_aBcDeFgHi`) |
| `VITE_COGNITO_CLIENT_ID` | App Client ID |
| `VITE_COGNITO_DOMAIN` | Hosted UI domain (e.g. `sudoku-auth.auth.eu-west-2.amazoncognito.com`) |

These are non-secret (safe to embed in frontend bundles). Set them as Amplify environment variables
in Terraform, following the same pattern as `VITE_API_URL`.

### Backend (application.properties / Lambda env)

No Cognito-specific env vars needed — the backend trusts API Gateway's injected claims only.

---

## 10. Local Development

For local development with `quarkus:dev`, disable JWT enforcement:

```properties
# application.properties (dev profile)
quarkus.http.auth.proactive=false
```

Use a mock `userId` (e.g. `local-dev-user`) injected via a dev-only request filter when no
Authorization header is present. This allows the backend to be developed and tested without a real
Cognito pool.

For the frontend dev server, Amplify can be configured to use the real Cognito pool, or a mock
auth provider can be implemented behind the `VITE_MOCK_API` flag.

---

## 11. Security Checklist

Before shipping auth to production, verify:

- [ ] No `allow_origins = ["*"]` anywhere (API Gateway CORS, Cognito callback URLs)
- [ ] JWT Authorizer attached to all `/games/*` and `/players/*` routes
- [ ] `userId` comes exclusively from JWT `sub`, never from request body or path
- [ ] DynamoDB queries always include `userId` as partition key
- [ ] `SudokuPlayers` table grants isolated from `SudokuGames` in IAM policy
- [ ] Google credentials passed as sensitive Terraform variables, injected via GitHub Actions secrets — never committed to the repository
- [ ] Refresh token rotation enabled on the Cognito App Client
- [ ] No `cognito-idp:*` permissions on the Lambda execution role
