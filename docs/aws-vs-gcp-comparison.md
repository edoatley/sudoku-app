# AWS ↔ GCP: how the two working deployments differ

One codebase, one container image, two clouds. This is a side-by-side of the **AWS** and **GCP**
deployments now that both run the games + player-profile slice end-to-end, so the differences are
about *platform wiring*, not application logic. It was written after finishing the GCP parity work
(gaps C–F) and fixing the two bugs that had blocked end-to-end GCP for two days (see
[The two bugs](#the-two-bugs-that-blocked-gcp) and [The debug loop that found them](#the-debug-loop-that-found-them)).

Reference code: `backend/src/main/resources/application.properties` (profile blocks),
`com/sudoku/auth/UserIdentityResolver.java`, `com/sudoku/web/filter/{CorsFilter,AllowedUsersFilter}.java`,
`infra/aws/*.tf`, `infra/gcp/*.tf`, `scripts/{github/gcp-smoke-token.sh,local/smoke-token-local.sh}`.

## The one thing that's the same

The **backend is a single fast-jar HTTP server** in one image (`backend/src/main/docker/Dockerfile.jvm-lwa`).
- On **AWS** the [AWS Lambda Web Adapter](https://github.com/awslabs/aws-lambda-web-adapter) is baked
  in as a Lambda extension that bridges API Gateway → Lambda → the app's HTTP port (`AWS_LWA_PORT=8080`).
- On **GCP** the same image runs unmodified on **Cloud Run** as a plain HTTP server on `:8080`; the
  Lambda adapter is inert outside a Lambda environment.

Everything below is a consequence of the platform the image lands on. The runtime picks its behaviour
from the Quarkus **config profile** (`QUARKUS_PROFILE=gcp` with `QUARKUS_CONFIG_PROFILE_PARENT=prod`
on Cloud Run; default + `%prod` on Lambda) and a handful of env vars — no code branches per cloud.

## At a glance

| Dimension | AWS | GCP |
|---|---|---|
| Compute | Lambda (container) behind API Gateway | Cloud Run (container), direct |
| Region | `eu-west-2` (London) | `us-central1` |
| **Token validation (auth edge)** | **API Gateway JWT Authorizer** validates; the Lambda *trusts* the claims (`quarkus.oidc.application-type=service`, never re-validates) | **In-app**: Quarkus OIDC validates every request (no edge authorizer on Cloud Run) |
| Identity provider | Amazon Cognito user pool | Identity Platform / Firebase Auth |
| OIDC issuer | `cognito-idp.eu-west-2.amazonaws.com/<pool>` (discovery on) | `securetoken.google.com/<project>` (discovery **off**, explicit JWKS — Firebase isn't OIDC-discoverable) |
| Token audience | Cognito client id | GCP project id |
| **CORS** | Handled at **API Gateway** | **In-app** `CorsFilter` (`%gcp.sudoku.cors.filter.enabled=true`) |
| Persistence | DynamoDB (`sudoku.persistence=dynamodb`) | Firestore Native (`%gcp.sudoku.persistence=firestore`) |
| Persistence auth | Lambda execution role (IAM) | Cloud Run runtime SA `sudoku-run@` via ADC (`roles/datastore.user`) |
| AI coach (Bedrock) | Direct — Lambda role has Bedrock IAM | **Cross-cloud** — AWS keys from Secret Manager mounted as `AWS_*` env; region stays `eu-west-2` |
| Frontend hosting | AWS Amplify | Firebase Hosting (SPA rewrites in `ui/firebase.json`) |
| Public reachability | API Gateway stage | Cloud Run `allUsers` invoker on non-`default` workspaces (CP-GCP-014); app still enforces auth |
| Test-token minting | Cognito `USER_PASSWORD_AUTH` (`scripts/local/smoke-token-local.sh`) | Identity Platform `signInWithPassword` (`scripts/github/gcp-smoke-token.sh`) |
| IaC | `infra/aws/` (Terraform) | `infra/gcp/` (Terraform) + `scripts/infra/gcp/bootstrap.sh` for one-time project/SA/API/IAM setup |
| Env isolation | per-env tables | Firestore **named database** per Terraform workspace (`(default)` vs `sudoku<suffix>`) |

## Where the real differences live

### 1. The auth edge — validate at the door vs validate in-app
This is the single biggest architectural divergence.

- **AWS**: API Gateway's JWT Authorizer verifies the Cognito token *before* the Lambda runs. The
  Lambda is configured `quarkus.oidc.application-type=service` and **trusts** the injected claims —
  it never re-validates. Auth failures are rejected at the edge and never reach application code.
- **GCP**: Cloud Run has no edge authorizer, so the app validates every request itself. The `%gcp`
  profile pins the issuer, disables OIDC discovery, and points at Firebase's fixed JWKS
  (`.../robot/v1/metadata/jwk/securetoken@system.gserviceaccount.com`), with `audience = <project>`.

Consequence: **all token-shape bugs surface only on GCP**, because AWS never parses the token in-app.
That is exactly why [bug #1](#bug-1--401-on-every-gcp-request) only ever bit GCP.

### 2. Canonical identity (`userId`) — same goal, provider-specific derivation
`UserIdentityResolver` keys every user's data on a **stable, cross-cloud identity** so a user is the
same regardless of which cloud brokered the login:
- **Cognito token** (no `firebase` claim) → `jwt.getSubject()` (the Cognito subject).
- **Firebase token** (`firebase` claim) → strict provider allow-list: `google.com` → the Google `sub`
  from `firebase.identities["google.com"]`; `password` → `firebase:<uid>` (the smoke-test user only);
  anything else → 401.

### 3. CORS — gateway vs in-app filter
On AWS, API Gateway answers CORS. On GCP there's no gateway, so the app's `CorsFilter` does it,
enabled only under `%gcp`. Two GCP-specific traps we hit and encoded in `application.properties`:
- `quarkus.http.cors` is a **build-time** property, so a `%gcp`-only enable never takes effect in the
  shared image — hence a runtime JAX-RS filter instead of native Quarkus CORS.
- **Do not** add a `quarkus.http.auth.permission` for `OPTIONS`: a methods-scoped permission on `/*`
  claims every path and then denies `GET/POST/PATCH` (403). The auto-generated preflight response
  isn't gated by annotation auth, so the filter answers it with no extra config.

### 4. Persistence — adapter chosen at runtime
The `*RepositoryProducer` beans select DynamoDB vs Firestore adapters on `sudoku.persistence`, and
only the resolvable adapter is instantiated, so the unused cloud SDK never initialises. Env isolation
differs: AWS uses per-env table names; GCP uses a **named Firestore database per Terraform workspace**
(`main.tf`: `(default)` for the default workspace, else `sudoku<suffix>`).

### 5. AI coach (Bedrock) — direct vs cross-cloud credentials
`BedrockClientProducer` builds a `BedrockRuntimeClient` with the **default AWS credential chain** and
region `eu-west-2` — unchanged across clouds. What changes is where the credentials come from:
- **AWS**: the Lambda execution role carries Bedrock IAM directly.
- **GCP**: there is no AWS identity, so real AWS keys live in **Secret Manager** and are mounted as
  the standard `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` env vars (only when `enable_coach=true`).
  The same default chain resolves them — no code change. It calls Bedrock in `eu-west-2` *from*
  `us-central1`.

### 6. Test-token minting — deterministic, no browser
Both clouds have a REST way to mint a real ID token for a password test user, so CI/agents never need
a browser or copy-paste:
- **AWS**: Cognito `USER_PASSWORD_AUTH` — `scripts/local/smoke-token-local.sh`.
- **GCP**: Identity Platform `accounts:signInWithPassword` — `scripts/github/gcp-smoke-token.sh`,
  with `scripts/infra/gcp/create-smoke-user.sh` provisioning the user (CP-GCP-032). The GCP token
  carries `firebase.sign_in_provider=password`, so `UserIdentityResolver` maps it to `firebase:<uid>`;
  the email must be on `%prod.app.allowed.emails` and `email_verified=true`.

### 7. IaC & bootstrap
AWS is pure Terraform. GCP is Terraform **plus** a one-time `scripts/infra/gcp/bootstrap.sh` that owns
what Terraform deliberately doesn't: project creation, API enablement, the runtime service accounts,
and their `roles/datastore.user` grant (kept out of Terraform on purpose — see the note in
`infra/gcp/coach.tf`). If you grant a runtime role or enable an API by hand, it belongs in
`scripts/infra/gcp/bootstrap.sh`, not Terraform.

## The two bugs that blocked GCP

Both were invisible on AWS and only appeared once real Firebase tokens flowed through the in-app path.

### Bug 1 — 401 on every GCP request
`UserIdentityResolver.resolveFirebase()` compared the nested `firebase.sign_in_provider` claim against
`String` literals. Under real OIDC that nested value is a `jakarta.json.JsonString`, **never equal** to
a `java.lang.String` — so every real Firebase token fell through to 401. Unit tests passed only because
they stubbed the claim as a plain `Map<String,String>`. **Fix** (`b83356e`): coerce nested claim values
via `asString()` (handles both `JsonString` and `String`) + JSON-P regression tests. AWS was immune
because its Lambda never parses the token (see [§1](#1-the-auth-edge--validate-at-the-door-vs-validate-in-app)).

### Bug 2 — Firestore `PERMISSION_DENIED` on every op
Once auth passed, every Firestore call returned `PERMISSION_DENIED` — even though `sudoku-run@` had
`roles/datastore.user` (verified `GRANTED` via the IAM Policy Troubleshooter), no deny/org policies,
and the API enabled. Root cause: because `quarkus-oidc` is on the classpath, the `quarkus-google-cloud`
extension defaults **`access-token-enabled=true`**, which makes the Firestore client authenticate with
the **caller's incoming Firebase token** instead of ADC. Firestore then saw the *end user* (who has no
`datastore.user`) and denied everything. **Fix** (`b9461f8`):
`quarkus.google.cloud.access-token-enabled=false`, so the client uses ADC → the Cloud Run runtime SA.
Verified live: `GET /players/me` → 200, `POST /games` → 201.

## The debug loop that found them

The first day-and-a-half was a slow manual loop (browser → paste console/network into chat → reason →
ask for a `gcloud` log → repeat), made worse by copy-paste **corrupting bearer tokens**, which
conflated "is auth broken?" with "is the token transport broken?". What actually worked was a
**two-layer loop** that removed the human from the inner loop:

1. **Layer 1 — auth, no browser.** Mint a token over REST (`gcp-smoke-token.sh`) and `curl` the deployed
   backend directly. Deterministic, no copy-paste, isolates backend auth from UI transport. This is
   what exposed both bugs in minutes: token *verifies* → resolver throws 401 (bug 1); then requests
   *authenticate* → Firestore denies (bug 2).
2. **Layer 2 — UI transport.** Only after Layer 1 is green, drive the real UI with Playwright (Firebase
   session seeded from a minted token, same-origin via the Vite dev proxy). *(Not yet wired — the API
   path is proven green via Layer 1; tracked as the remaining Layer-2 item.)*

Lesson: when a request crosses an edge (browser → cloud, app → managed service), **isolate each hop
with a deterministic client before blaming the code**. A minted-token `curl` beats a browser round-trip
for every auth question.

## Still open / deferred (not parity-blocking)
- Layer-2 Playwright run against the deployed GCP backend (Firebase-seeded session).
- Automated CI smoke job wrapping `gcp-smoke-token.sh` (the user + minting exist; CP-GCP-032).
- Hosted-UI-per-RC Firebase Hosting targets (gap G3), custom-domain cutover, GCP budget hard-cap
  (CP-GCP-061), and a live Bedrock call verified on GCP.

## Related specs
`docs/specs/cloud-platform-specs.md` (CP-GCP-010/011/012 auth+CORS, CP-GCP-014 invoker, CP-GCP-020
Firestore provisioning, CP-GCP-021 Firestore I/O — the access-token fix, CP-GCP-032 test user,
CP-GCP-042/043 VITE wiring, CP-GCP-085 cross-cloud Bedrock); `docs/llds/user-management.md` (UM-GCP-002/003/004/006);
`docs/todo/gcp-aws-parity.md` (the gap tracker).
