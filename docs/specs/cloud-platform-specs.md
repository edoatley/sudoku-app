# Cloud Platform — EARS Specifications

## API Gateway

- [x] **CP-INFRA-001**: The system shall route all /puzzles/*, /health, and /dev/* requests to the Java Lambda without JWT validation.
- [x] **CP-INFRA-002**: The system shall require a valid Cognito JWT on all /api/v1/games/* and /players/me routes, validated by API Gateway before the Lambda is invoked.
- [x] **CP-INFRA-003**: The system shall route POST /api/v1/puzzles/import to the Image Recognition Lambda with JWT validation.
- [x] **CP-INFRA-004**: The system shall route GET /api/v1/puzzles/import/warmup to the Image Recognition Lambda without JWT validation.
- [x] **CP-INFRA-005**: The system shall apply throttling of burst=50 requests and rate=25 requests/second at the API Gateway stage.
- [x] **CP-INFRA-006**: The system shall write API access logs in JSON format to CloudWatch with 7-day retention in the default workspace and 3-day retention in all other workspaces.

## Lambda

- [x] **CP-INFRA-010**: The system shall deploy the Java Lambda with SnapStart enabled on published versions to minimise cold-start latency.
- [x] **CP-INFRA-011**: The system shall route API Gateway traffic to the Java Lambda via a "live" alias pointing to the latest published version.
- [x] **CP-INFRA-012**: The system shall deploy the Image Recognition Lambda as a container image to support native Python dependencies.

## CORS Lifecycle

- [x] **CP-INFRA-020**: The system shall apply baseline wildcard CORS origins at terraform apply time.
- [x] **CP-INFRA-021**: The post-apply CI workflow shall tighten CORS allowed origins to the exact Amplify URL after infrastructure is created.
- [x] **CP-INFRA-022**: The system shall ignore CORS configuration changes in Terraform state to preserve post-apply tightening across subsequent applies.

## Cognito

- [x] **CP-INFRA-030**: The system shall configure Cognito for social-only login via Google OAuth, with no native username/password sign-up.
- [x] **CP-INFRA-031**: The system shall maintain a single shared Cognito pool for all rc-* workspaces to avoid multiplying Google OAuth redirect URIs.
- [x] **CP-INFRA-032**: The system shall provision a smoke-test Cognito user with username/password auth for use by CI pipelines.

## Amplify & Frontend Delivery

- [x] **CP-INFRA-040**: The system shall disable Amplify auto-build and trigger builds explicitly via CI after terraform apply.
- [x] **CP-INFRA-041**: The system shall inject VITE_API_URL, VITE_COGNITO_USER_POOL_ID, VITE_COGNITO_CLIENT_ID, VITE_COGNITO_DOMAIN, VITE_MOCK_API, and VITE_DEV_TOOLS as Amplify environment variables at build time.
- [x] **CP-INFRA-042**: The system shall set VITE_DEV_TOOLS=false in the default (production) workspace and VITE_DEV_TOOLS=true in all other workspaces.

## DynamoDB

- [x] **CP-INFRA-050**: The system shall provision DynamoDB tables with PAY_PER_REQUEST billing.
- [x] **CP-INFRA-051**: The system shall enable point-in-time recovery on DynamoDB tables in the default workspace only.

## Workspace Isolation

- [x] **CP-INFRA-060**: The system shall append -{workspace} to all resource names in non-default workspaces to prevent naming collisions.
- [x] **CP-INFRA-061**: The system shall share the Lambda zip S3 bucket across all workspaces, with each workspace uploading to its own key prefix.
