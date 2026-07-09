# Cloud Platform — EARS Specifications

## API Gateway

- [x] **CP-INFRA-001**: The system shall route all /puzzles/* and /health requests, plus any other unmatched path, to the Java Lambda via the $default route without JWT validation.
- [x] **CP-INFRA-002**: The system shall require a valid Cognito JWT on all /api/v1/games/*, /players/me, and /ai/* routes, validated by API Gateway before the Lambda or Image Recognition Lambda is invoked.
- [x] **CP-INFRA-003**: The system shall route POST /api/v1/ai/image-to-puzzle to the Image Recognition Lambda with JWT validation.
- [x] **CP-INFRA-004**: The system shall route GET /api/v1/ai/image-to-puzzle/warmup to the Image Recognition Lambda without JWT validation.
- [x] **CP-INFRA-005**: The system shall apply throttling of burst=50 requests and rate=25 requests/second at the API Gateway stage.
- [x] **CP-INFRA-006**: The system shall write API access logs in JSON format to CloudWatch with 7-day retention in the default workspace and 3-day retention in all other workspaces.
- [x] **CP-INFRA-007**: The system shall require a valid Cognito JWT on GET /api/v1/admin/data/games and GET /api/v1/admin/data/players, validated by API Gateway before the Lambda is invoked. (Group-level authorization beyond JWT validity is enforced in the Lambda — see UM-BE-060/061.)

## Lambda

- [x] **CP-INFRA-010**: The system shall deploy the Java Lambda with SnapStart enabled on published versions to minimise cold-start latency.
- [x] **CP-INFRA-011**: The system shall route API Gateway traffic to the Java Lambda via a "live" alias pointing to the latest published version.
- [x] **CP-INFRA-012**: The system shall deploy the Image Recognition Lambda as a container image to support native Python dependencies.

## CORS Lifecycle

- [x] **CP-INFRA-020**: The system shall set API Gateway CORS allowed origins directly in Terraform to the static custom domain(s) for each workspace type, with no post-apply tightening step.
- [x] **CP-INFRA-021**: The system shall tighten Cognito app client callback/logout URLs via a post-apply CI workflow once the exact Amplify branch URL is known.
- [x] **CP-INFRA-022**: The system shall ignore Cognito callback/logout URL changes in Terraform state to preserve post-apply additions across subsequent applies.

## Cognito

- [x] **CP-INFRA-030**: The system shall configure Cognito for social-only login via Google OAuth, with no native username/password sign-up.
- [x] **CP-INFRA-031**: The system shall maintain a single shared Cognito pool for all rc-* workspaces to avoid multiplying Google OAuth redirect URIs.
- [x] **CP-INFRA-032**: The system shall provision a smoke-test Cognito user with username/password auth for use by CI pipelines.
- [x] **CP-INFRA-033**: The system shall NOT enable native username/password auth on the public web app client; CI pipelines shall instead authenticate the smoke-test user via a separate, secret-bearing smoke-test app client.

## Amplify & Frontend Delivery

- [x] **CP-INFRA-040**: The system shall disable Amplify auto-build and trigger builds explicitly via CI after terraform apply.
- [x] **CP-INFRA-041**: The system shall inject VITE_API_URL, VITE_COGNITO_USER_POOL_ID, VITE_COGNITO_CLIENT_ID, VITE_COGNITO_DOMAIN, VITE_MOCK_API, VITE_DEV_TOOLS, and VITE_AI_COACH as Amplify environment variables at build time.
- [x] **CP-INFRA-042**: The system shall set VITE_DEV_TOOLS=false in the default (production) workspace and VITE_DEV_TOOLS=true in all other workspaces.

## DynamoDB

- [x] **CP-INFRA-050**: The system shall provision DynamoDB tables with PAY_PER_REQUEST billing.
- [x] **CP-INFRA-051**: The system shall enable point-in-time recovery on the Games, Players, and Leaderboard tables in the default workspace only (not the ephemeral, TTL-based CoachRateLimits table).
- [x] **CP-INFRA-052**: The system shall enable deletion protection on the Games, Players, and Leaderboard tables in the default workspace only.

## Workspace Isolation

- [x] **CP-INFRA-060**: The system shall append -{workspace} to all resource names in non-default workspaces to prevent naming collisions.
- [x] **CP-INFRA-061**: The system shall share the Lambda zip S3 bucket across all workspaces, with each workspace uploading to its own key prefix.
