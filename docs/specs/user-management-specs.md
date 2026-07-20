# User Management — EARS Specifications

## Player Profile

- [x] **UM-BE-001**: When GET /players/me is called for a user with no existing profile, the system shall create a new PlayerProfile using the userId, email, and display name from the JWT claims and persist it to DynamoDB.
- [x] **UM-BE-002**: When GET /players/me is called for a user with an existing profile, the system shall return the stored profile unchanged, even if JWT claims have changed.
- [x] **UM-BE-003**: When PATCH /players/me is called with a valid request body, the system shall load the existing PlayerProfile, apply non-null fields from the request, set updatedAt to the current UTC instant, and persist the result via upsert.
- [x] **UM-BE-004**: When PATCH /players/me is called and no PlayerProfile exists for the authenticated user, the system shall return HTTP 404.
- [x] **UM-BE-005**: When PATCH /players/me is called with displayName, avatarKey, and aiCoachEnabled all absent or null, the system shall return HTTP 400 with a JSON error body.
- [x] **UM-BE-006**: When PATCH /players/me is called with a displayName that is blank or exceeds 50 characters after trimming, the system shall return HTTP 400 with a JSON error body.
- [x] **UM-API-001**: The system shall expose GET /players/me requiring JWT authentication and returning the player's PlayerProfile.
- [x] **UM-API-002**: The system shall expose PATCH /players/me requiring JWT authentication, accepting a JSON body with optional displayName, avatarKey, and aiCoachEnabled fields, and returning the updated PlayerProfile.
- [x] **UM-DATA-001**: The system shall store player profiles in DynamoDB with userId as the sole partition key (no sort key).
- [x] **UM-DATA-002**: The system shall store createdAt and updatedAt as ISO-8601 UTC strings on every PlayerProfile.
- [x] **UM-DATA-003**: The system shall store avatarKey as a nullable string attribute on every PlayerProfile; profiles created before this field existed shall return avatarKey as null.

## Frontend — Profile Update

- [x] **UM-UI-001**: When the user opens the Edit Profile dialog, the system shall initialise the displayName field with playerProfile.displayName and the avatar picker with playerProfile.avatarKey, falling back to 'Person' if avatarKey is null.
- [x] **UM-UI-002**: When playerProfile.avatarKey is not present in the known avatar icon list, the system shall display the 'Person' avatar icon as a fallback.
- [x] **UM-UI-003**: While the Edit Profile save is in-flight, the system shall disable the Save button and show a loading indicator.
- [x] **UM-UI-004**: When the Edit Profile save succeeds, the system shall update playerProfile state and avatar state in the UI and close the dialog.
- [x] **UM-UI-005**: When the Edit Profile save fails, the system shall display an inline error Alert within the dialog and leave the dialog open.
- [x] **UM-UI-006**: When the displayName field in Edit Profile is blank, the system shall disable the Save button.
- [x] **UM-UI-007**: The system shall replace the "Change Avatar" menu item in the account menu with an "Edit Profile" menu item that opens the Edit Profile dialog.
- [x] **UM-UI-008**: When VITE_MOCK_API is true, the system shall simulate a successful PATCH by merging the submitted fields onto the current mock profile without making a network call.

## Authentication

- [x] **UM-BE-010**: The system shall validate all JWT tokens on protected routes using the Cognito issuer URL and client ID before the request reaches any business logic.
- [x] **UM-BE-011**: The system shall extract the userId (Cognito sub claim) from the validated JWT principal for all authenticated operations.
- [x] **UM-BE-012**: The system shall extract the email and display name claims from the JWT to seed a new PlayerProfile.

## Email Allowlist

- [x] **UM-BE-020**: While the app.allowed.emails configuration property is non-empty, the system shall reject requests from authenticated users whose email is not in the allowlist with HTTP 403 and a JSON error body.
- [x] **UM-BE-021**: While the app.allowed.emails configuration property is empty, the system shall allow all authenticated requests without email checking.
- [x] **UM-BE-022**: The system shall pass unauthenticated requests (public routes) through the allowlist filter without rejection.

## Admin Authorization

- [x] **UM-BE-060**: While a request carries a JWT whose cognito:groups claim contains the configured admin group (app.admin.group, default "administrators"), the system shall allow access to /admin/* endpoints.
- [x] **UM-BE-061**: While an authenticated (non-anonymous) request's cognito:groups claim does not contain the configured admin group, the system shall reject /admin/* requests with HTTP 403 and a JSON error body.
- [x] **UM-BE-062**: Where the identity is anonymous (dev, it, or test build profile with no OIDC), the system shall not apply the admin group check.
- [x] **UM-BE-064**: The system shall not expose /dev/data/games or /dev/data/players under any path — the unauthenticated full-table-scan resource formerly serving them has been removed from the codebase entirely.

## Developer & Test Isolation

- [x] **UM-BE-030**: Where the dev, it, or test build profile is active and no Authorization header is present, the system shall inject a mock SecurityContext with userId "local-dev-user".
- [x] **UM-BE-031**: Where the dev, it, or test build profile is active and an Authorization header is present, the system shall use the real JWT authentication flow.
- [x] **UM-BE-032**: The DevUserFilter shall be compiled out of the production build artifact entirely.

## CORS

- [x] **UM-BE-040**: When an HTTP OPTIONS preflight request is received from an allowed origin, the system shall respond with HTTP 200 and CORS headers without invoking any business logic handler.
- [x] **UM-BE-041**: The system shall add Access-Control-Allow-Origin, Access-Control-Allow-Methods, and Access-Control-Allow-Headers to all responses from allowed origins.
- [x] **UM-BE-042**: The system shall read the list of allowed CORS origins from the sudoku.cors.allowed-origins configuration property.

## API Logging

- [x] **UM-BE-050**: Where sudoku.api.logging.enabled is true, the system shall log the HTTP method, path, and request body for every incoming request.
- [x] **UM-BE-051**: Where sudoku.api.logging.enabled is true, the system shall log the HTTP method, path, status code, and response body for every outgoing response.
- [x] **UM-BE-052**: After reading the request body for logging, the system shall reset the input stream so the downstream handler can read the body again.

## Multi-Cloud Auth & Identity (GCP)

Authentication and identity behaviours on GCP. Active gaps until the games-slice arrow's code phase implements them. (Existing `## Authentication` specs describe the AWS/Cognito path — validated at the API Gateway authorizer; these govern the GCP/Firebase path — validated in-app.)

- [ ] **UM-GCP-001**: On GCP the system shall validate the caller's JWT in-app (there is no edge authorizer) and reject any request to a non-public route that lacks a valid token.
- [ ] **UM-GCP-002**: On GCP the system shall validate tokens against the Identity Platform issuer https://securetoken.google.com/<project_id> and audience <project_id> using an explicitly configured JWKS, with OIDC discovery disabled.
- [ ] **UM-GCP-003**: The system shall derive the canonical userId by a strict provider allow-list: for firebase.sign_in_provider = google.com, the Google sub from firebase.identities["google.com"]; for password, the value firebase:<uid>; for any other provider, the system shall reject the request (401).
- [ ] **UM-GCP-004**: The system shall namespace password-provider identities as firebase:<uid>, disjoint from the raw Google sub, so a password token can never resolve onto a Google user's data.
- [ ] **UM-GCP-005**: On GCP the system shall apply the email allowlist identically to AWS, requiring the token's email claim to be on app.allowed.emails regardless of sign-in provider.
- [ ] **UM-GCP-006**: On GCP the system shall answer CORS preflight OPTIONS requests before authentication, so a cross-origin preflight (which carries no Authorization header) is not rejected.
- [ ] **UM-GCP-007**: Where the build property sudoku.persistence=firestore, the system shall persist player profiles in a Firestore players collection keyed by the canonical userId, with lazy-creation and PATCH-update behaviour unchanged.
- [D] **UM-GCP-008**: The system shall enforce administrator authorization on GCP via a custom claim or configured allowlist. (Deferred — /admin/* is not part of the games + player-profile slice; Identity Platform has no group concept.)
