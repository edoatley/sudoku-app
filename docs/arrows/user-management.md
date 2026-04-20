# Arrow: User Management

Player profile lazy-creation and update, JWT-based auth, email allowlist, CORS, and dev/test isolation filters.

## Status

**OK** - 2026-04-20. Profile update (PATCH /players/me, avatarKey field, EditProfileDialog) implemented, tested, and verified.

## References

### HLD
- docs/high-level-design.md — "Authentication Architecture", "Dev/Test Isolation" sections

### LLD
- docs/llds/user-management.md

### EARS
- docs/specs/user-management-specs.md (22 specs, all [x])

### Tests
- backend/src/test/java/com/sudoku/player/PlayerResourceTest.java — covers UM-BE-001 to 002, UM-API-001 to 002, UM-DATA-001 to 003; @spec annotations added
- backend/src/test/java/com/sudoku/player/PlayerServiceImplUpdateTest.java — covers UM-BE-003 to 006; @spec annotations added
- backend/src/test/java/com/sudoku/auth/AllowedUsersFilterTest.java — covers UM-BE-010 to 012, UM-BE-020 to 022; @spec annotations added

### Code
- backend/src/main/java/.../player/PlayerResource.java
- backend/src/main/java/.../player/PlayerService.java
- backend/src/main/java/.../player/PlayerServiceImpl.java
- backend/src/main/java/.../player/PlayerRepository.java
- backend/src/main/java/.../player/DynamoDbPlayerRepository.java
- backend/src/main/java/.../player/PlayerItem.java
- backend/src/main/java/.../player/PlayerProfile.java
- backend/src/main/java/.../player/PlayerUpdateRequest.java
- backend/src/main/java/.../player/PlayerNotFoundException.java
- backend/src/main/java/.../player/InvalidPlayerUpdateException.java
- backend/src/main/java/.../exception/PlayerNotFoundExceptionMapper.java
- backend/src/main/java/.../exception/InvalidPlayerUpdateExceptionMapper.java
- backend/src/main/java/.../auth/AllowedUsersFilter.java
- backend/src/main/java/.../developer/DevUserFilter.java
- backend/src/main/java/.../developer/DevDatabaseInitializer.java
- backend/src/main/java/.../cors/CorsFilter.java
- backend/src/main/java/.../logging/ApiLoggingFilter.java
- backend/src/main/resources/application.properties
- ui/src/components/EditProfileDialog.jsx
- ui/src/hooks/usePlayerProfile.js
- ui/src/api/sudokuApi.js

## Architecture

**Purpose:** Manage player identity and enforce security policies on every request.

**Key Components:**
1. `PlayerResource` / `PlayerServiceImpl` / `DynamoDbPlayerRepository` — lazy profile creation on first authenticated request
2. `AllowedUsersFilter` — email allowlist enforcement (defence-in-depth behind API GW JWT authorizer)
3. `DevUserFilter` — mock SecurityContext injection in dev/it/test profiles; compiled out of production
4. `CorsFilter` — OPTIONS preflight short-circuit and CORS header injection
5. `ApiLoggingFilter` — full request/response body logging (dev only)

## EARS Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| Player Profile (backend) | UM-BE-001 to 006, UM-API-001 to 002, UM-DATA-001 to 003 | 11 | 0 | 0 |
| Frontend Profile Update | UM-UI-001 to 008 | 8 | 0 | 0 |
| Authentication | UM-BE-010 to 012 | 3 | 0 | 0 |
| Email Allowlist | UM-BE-020 to 022 | 3 | 0 | 0 |
| Dev & Test Isolation | UM-BE-030 to 032 | 3 | 0 | 0 |
| CORS | UM-BE-040 to 042 | 3 | 0 | 0 |
| API Logging | UM-BE-050 to 052 | 3 | 0 | 0 |

**Summary:** 34 of 34 active specs implemented; 0 deferred; 0 gaps.

## Key Findings

1. **Email frozen at creation** — `PlayerServiceImpl.getOrCreateProfile()` does not update email from JWT claims on subsequent GET calls. Email is read-only after the first login. Display name and avatar are updatable via PATCH /players/me.
2. **Production allowlist in source code** — `app.allowed.emails` production value is hardcoded in `application.properties`. Adding a new user requires a code commit and redeploy. [D — low-churn allowlist; acceptable for personal project]
3. **`AllowedUsersFilter` anonymous passthrough** — Unauthenticated requests pass the allowlist silently. If OIDC fails to populate `SecurityIdentity` for an authenticated route, the request slips through. API Gateway JWT authorizer is the primary enforcement point.
4. **`ApiLoggingFilter` body buffering** — Reads entire request body into memory for logging; doubles peak memory on large payloads. Current exposure is low (no large payloads on this Lambda path).
5. **PATCH read-modify-write race** — Two concurrent PATCHes from the same user can race; last write wins. Acceptable for single-user personal app.
