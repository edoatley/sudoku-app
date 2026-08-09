# Image Recognition — EARS Specifications

## Input Handling

- [x] **IR-API-001**: The system shall expose POST /api/v1/ai/image-to-puzzle accepting a JSON body with a base64-encoded image field, requiring JWT authentication.
- [x] **IR-API-002**: The system shall expose GET /api/v1/ai/image-to-puzzle/warmup requiring no authentication and returning HTTP 200 without invoking the Bedrock model.
- [x] **IR-BE-001**: If the request body is not valid JSON or the image field is absent or empty, the system shall return HTTP 400.
- [x] **IR-BE-002**: If the decoded image exceeds 8 MB, the system shall return HTTP 400.

## Image Preprocessing

- [D] **IR-PROC-001**: Where PIL is available, the system shall downscale images whose longest edge exceeds 800px using LANCZOS resampling before sending to Bedrock.
- [D] **IR-PROC-002**: Where PIL is available and the image has an alpha channel, the system shall composite the image onto a white background before processing.
- [D] **IR-PROC-003**: Where PIL is available, the system shall desaturate the image to greyscale before sending to Bedrock.
- [D] **IR-PROC-004**: Where PIL is available, the system shall encode the processed image as JPEG at quality 85 before sending to Bedrock.
- [D] **IR-PROC-005**: If PIL processing fails for any reason, the system shall fall back to sending the original image bytes without preprocessing.
- [x] **IR-PROC-006**: The system shall detect image format from magic bytes (PNG, JPEG, GIF, WebP) and default to JPEG for unknown formats.

## Bedrock Invocation

- [x] **IR-PROC-010**: The system shall invoke the Bedrock Converse API (not InvokeModel) to support a system prompt alongside the user message.
- [x] **IR-PROC-011**: The system shall use temperature=0 and maxTokens=2048 for all Bedrock invocations.
- [x] **IR-PROC-012**: The system shall instruct the model to first transcribe the grid in a pipe-delimited scratchpad, then output the final result as JSON wrapped in `<json>` tags.
- [x] **IR-PROC-013**: The system prompt shall instruct the model to ignore cell background colour (orange, yellow, tan, grey shading) and treat a cell as empty if no digit is printed in it, regardless of its background colour.

## Grid Parsing

- [x] **IR-PROC-020**: The system shall first attempt to extract the grid from `<json>...</json>` tags in the model response, falling back to the first `{...}` block if tags are absent.
- [x] **IR-PROC-021**: If JSON extraction fails, the system shall attempt to parse the pipe-delimited scratchpad, accepting rows with exactly 9 digit or empty-cell tokens.
- [x] **IR-PROC-022**: The system shall coerce string digit values (e.g., "5") to integers when parsing JSON grid output.
- [x] **IR-PROC-023**: If neither JSON nor pipe-table parsing yields a valid 9×9 grid, the system shall raise an error and return HTTP 422.

## Grid Validation & Scoring

- [x] **IR-PROC-030**: The system shall score each candidate grid: +2 if no row/column/block duplicates are present, plus +1 for every 10 clues above the minimum plausible threshold of 10.
- [x] **IR-PROC-031**: The system shall discard any candidate grid with fewer than 10 filled cells as implausibly sparse.
- [x] **IR-PROC-032**: The system shall set validPuzzle=true in the response only when the grid has no duplicates and at least 17 filled cells.
- [x] **IR-PROC-033**: The system shall return the highest-scored grid even if it contains duplicates, with validPuzzle=false, rather than returning HTTP 422 when any grid was extracted.

## Response

- [x] **IR-API-010**: On success, the system shall return HTTP 200 with originalGrid (9×9 integer array), validPuzzle (boolean), and modelName (string).
- [x] **IR-API-011**: If no grid can be extracted or all models fail, the system shall return HTTP 422 with a JSON error body.
- [x] **IR-API-012**: If an unexpected internal error occurs, the system shall return HTTP 500 with "Internal server error".

## Multi-Cloud Deployment (GCP)

Behaviours of the image-recognition service on GCP, where it runs as its own Cloud Run HTTP service
(there is no shared API Gateway as on AWS). The recognition logic (`handler.py`) is identical on
both clouds; a thin FastAPI front (`app.py`) adapts it to HTTP and adds the edge behaviours API
Gateway provides on AWS. Frontend reaches it via `VITE_IMAGE_RECOGNITION_URL` (see CP-GCP-042).

- [x] **IR-GCP-001**: On GCP the system shall serve image recognition as an HTTP service on port 8080 (POST /ai/image-to-puzzle, GET /ai/image-to-puzzle/warmup), translating each request into the API-Gateway-proxy event shape the recognition handler already consumes.
- [x] **IR-GCP-002**: On GCP the system shall validate the caller's Firebase (Identity Platform) JWT in-app on POST /ai/image-to-puzzle (RS256 against the securetoken JWKS, issuer https://securetoken.google.com/{project_id}, audience {project_id}, email_verified true), rejecting missing/invalid tokens with 401/403 — the endpoint is Bedrock-backed, so it is never left unauthenticated.
- [x] **IR-GCP-003**: On GCP the system shall apply CORS in-app from CORS_ALLOWED_ORIGINS (the workspace's Hosting origin and localhost for RC workspaces), answering preflight OPTIONS without invoking recognition.
- [x] **IR-GCP-004**: The warmup probe (GET /ai/image-to-puzzle/warmup) shall be reachable without a token and shall never invoke Bedrock, so the frontend can warm the service before sign-in.
- [x] **IR-GCP-005**: On GCP the image-recognition service shall invoke AWS Bedrock cross-cloud using the Secret Manager credentials wired when enable_coach = true (shared with the coach; CP-GCP-085).
