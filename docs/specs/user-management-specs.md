# User Management — EARS Specifications

## Player Profile

- [x] **UM-BE-001**: When GET /players/me is called for a user with no existing profile, the system shall create a new PlayerProfile using the userId, email, and display name from the JWT claims and persist it to DynamoDB.
- [x] **UM-BE-002**: When GET /players/me is called for a user with an existing profile, the system shall return the stored profile unchanged, even if JWT claims have changed.
- [x] **UM-API-001**: The system shall expose GET /players/me requiring JWT authentication and returning the player's PlayerProfile.
- [x] **UM-DATA-001**: The system shall store player profiles in DynamoDB with userId as the sole partition key (no sort key).
- [x] **UM-DATA-002**: The system shall store createdAt and updatedAt as ISO-8601 UTC strings on every PlayerProfile.

## Authentication

- [x] **UM-BE-010**: The system shall validate all JWT tokens on protected routes using the Cognito issuer URL and client ID before the request reaches any business logic.
- [x] **UM-BE-011**: The system shall extract the userId (Cognito sub claim) from the validated JWT principal for all authenticated operations.
- [x] **UM-BE-012**: The system shall extract the email and display name claims from the JWT to seed a new PlayerProfile.

## Email Allowlist

- [x] **UM-BE-020**: While the app.allowed.emails configuration property is non-empty, the system shall reject requests from authenticated users whose email is not in the allowlist with HTTP 403 and a JSON error body.
- [x] **UM-BE-021**: While the app.allowed.emails configuration property is empty, the system shall allow all authenticated requests without email checking.
- [x] **UM-BE-022**: The system shall pass unauthenticated requests (public routes) through the allowlist filter without rejection.

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
