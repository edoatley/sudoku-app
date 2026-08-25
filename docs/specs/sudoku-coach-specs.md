# Sudoku Coach — EARS Specifications

## Input Validation (Backend)

- [x] **SC-API-001**: When `POST /ai/coach` is called without a valid Cognito JWT, the system shall return 401 (enforced by API Gateway before Lambda invocation).
- [x] **SC-API-002**: When the board field is null, not a 9×9 array, or contains any integer outside 0–9, the system shall return 400.
- [x] **SC-API-003**: When the userMessage field is null, blank, or exceeds 500 characters, the system shall return 400.
- [x] **SC-API-004**: When the history array contains more than 6 messages, the system shall trim it to the last 6 messages rather than rejecting the request.

## Deterministic Pre-Analysis (Backend)

- [x] **SC-BE-001**: When a coaching request is received, the system shall call the existing hint engine on the submitted board before making any call to Bedrock.
- [x] **SC-BE-002**: When the hint engine finds no applicable strategy because the board is already solved, the system shall return 204 without calling Bedrock.
- [x] **SC-BE-003**: When the hint engine returns a result, the system shall include the technique name and all three escalating explanation levels (`nudge`, `focus`, `reveal`) from that result as labeled context in the Bedrock prompt.
- [x] **SC-BE-004**: The system shall format the board as a human-readable row-by-row string with box separators before injecting it into the Bedrock prompt, and shall not use `Arrays.deepToString` or any equivalent dense array format.

## Coordination and Content Logging (Backend)

- [x] **SC-BE-005**: When `BedrockCoachClient.call()` logs a `COACH_REQUEST` event, the system shall include the full `userMessage` text as received from the player.
- [x] **SC-BE-006**: When `BedrockCoachClient.call()` logs a `COACH_REQUEST` event, the system shall include the board's placed-digit grid (the raw `Grid` — distinct from the human-readable prompt format specified in SC-BE-004).
- [x] **SC-BE-007**: When `BedrockCoachClient.call()` logs a `COACH_REQUEST` event, the system shall include the full per-cell candidates grid, computed once by the caller (`SudokuCoachServiceImpl`) and passed in — not recomputed inside `BedrockCoachClient`.
- [x] **SC-BE-008**: When `BedrockCoachClient.call()` logs a `COACH_RESPONSE` event, the system shall include the full `aiMessage` text that was returned to the caller, on every path — the actual Bedrock response text on success, or the deterministic nudge text on the fallback path.
- [x] **SC-BE-009**: When the deterministic hint engine returns a `Found` result, the system shall delegate to `BedrockCoachClient.call()` exactly once to obtain the coaching response; it shall not call `BedrockCoachClient` for `PuzzleSolved` or `NoStrategyApplied` outcomes.
- [x] **SC-BE-019**: The system shall generate one `cid` (correlation ID) per `BedrockCoachClient.call()` invocation and include it in both the `COACH_REQUEST` and `COACH_RESPONSE` log lines for that call, so the two can be joined.
- [x] **SC-BE-020**: The system shall include `pid` (the gameId supplied on the coach request) in both the `COACH_REQUEST` and `COACH_RESPONSE` log lines, logging `pid` as null when the request omits a gameId, so coach turns can be joined with puzzle-play events for the same game.

## Bedrock Integration (Backend)

- [x] **SC-BE-010**: The system shall make exactly one Bedrock call per coaching request; it shall not use an agentic tool-calling loop.
- [x] **SC-BE-011**: The system shall mark the tutor system prompt for prompt caching using the cache mechanism appropriate to the active `coach.bedrock.api-mode`: `cache_control: {type: ephemeral}` on the system block in `invoke` mode, or a `CachePointBlock` appended to the system content blocks in `converse` mode; both cache Bedrock's KV state within a TTL window (5 minutes by default).
- [x] **SC-BE-012**: The system shall ensure the system prompt is at least 4,096 tokens (the minimum Bedrock caching threshold for Claude Haiku models, confirmed against real Bedrock traffic), using inline few-shot coaching examples or additional pedagogical guidance to reach this length if necessary. (Measured ~4,274 tokens after trimming the redundant `OUTPUT FORMAT`/`FINAL REMINDER` prose and re-padding with genuinely useful few-shot examples and context clarifications.)
- [x] **SC-BE-013**: The system shall inject the conversation history (after trimming) and the player's current message into the Bedrock prompt in chronological order.
- [x] **SC-BE-014**: The system shall instruct the LLM via the system prompt to return only valid JSON matching `{ "aiMessage": "...", "revealHint": true|false, "responseType": "..." }` and no other text; this prompt instruction is advisory — the schema itself is enforced server-side by Bedrock structured output (SC-BE-025), so the prompt is no longer the sole guarantor of well-formed JSON.
- [x] **SC-BE-015**: When the Bedrock call times out or exceeds 6 seconds, the system shall return a 200 response using the deterministic hint's nudge text as `aiMessage` and `revealHint: false`.
- [x] **SC-BE-016**: When the Bedrock response cannot be parsed as the expected JSON schema, the system shall fall back to the deterministic hint's nudge text as `aiMessage` and `revealHint: false`.
- [x] **SC-BE-017**: The system shall never return a 5xx status code due to a Bedrock failure; all AI failures shall degrade to the nudge-text fallback and return 200.
- [x] **SC-BE-018**: When building the `COACH_REQUEST` or `COACH_RESPONSE` log line, the system shall serialize it as JSON via a JSON library rather than string templating, so that `userMessage` and `aiMessage` values containing quotes, newlines, or other characters requiring escaping still produce a valid, parseable JSON log line.
- [x] **SC-BE-021**: When the Bedrock response cannot be parsed as the expected JSON schema or its `aiMessage` field is blank, the `COACH_RESPONSE` log line shall record `fallback: true` with a non-null `errorMsg` describing why, even though `bedrockRuntimeClient.invokeModel()` itself did not throw — this failure must not be logged identically to a genuine successful reply.
- [x] **SC-BE-022**: Before parsing the Bedrock response text as JSON, the system shall extract the first top-level `{...}` object from the text rather than assuming the entire text is bare JSON, so a response wrapped in markdown code fences or surrounding prose does not needlessly trigger the fallback path.
- [x] **SC-BE-023**: When `parseResponse()` falls back due to a blank `aiMessage` or a JSON-parse failure, the `COACH_RESPONSE` log line shall include the raw Bedrock response text (`rawResponseText`) that could not be parsed, so the failure can be diagnosed without reproducing it; on a genuine successful parse, `rawResponseText` shall be omitted from the log line entirely.
- [x] **SC-BE-024**: The system shall include a turn number derived from the trimmed conversation history's length, and a corresponding suggested escalation level (NUDGE, FOCUS, or REVEAL), in the Bedrock context block, without hard-restricting which of the three notes the LLM may draw upon in its response — an explicit request for the answer may still draw on the REVEAL note regardless of turn number.
- [x] **SC-BE-025**: The system shall constrain every Bedrock call to the JSON schema `{aiMessage: string (required), revealHint: boolean (required), responseType: string enum (required)}` with `additionalProperties: false`, via Bedrock's structured output feature — `output_config.format` in `invoke` mode, `outputConfig.textFormat` in `converse` mode — so a reply violating the schema cannot be returned by Bedrock regardless of prompt wording. This is one schema with all three fields defined together (`OUTPUT_SCHEMA_JSON`), not layered schemas.
- [x] **SC-BE-026**: The system shall select between `InvokeModel` (`coach.bedrock.api-mode=invoke`, default) and `Converse` (`coach.bedrock.api-mode=converse`) via a single configuration property; both modes shall enforce the structured-output schema of SC-BE-025 identically, so the flag isolates API-surface and caching-mechanism differences from the schema-enforcement behaviour.
- [x] **SC-BE-027**: When `coach.bedrock.api-mode=converse`, the system shall append a `CachePointBlock` to the system content blocks, and the tutor system prompt shall exceed the 4,096-token cache minimum of SC-BE-012, so cache writes and reads are observable via the `Converse` API's `usage` block (`cacheReadInputTokens`/`cacheWriteInputTokens`).
- [x] **SC-BE-028**: Within the schema of SC-BE-025, the `responseType` field shall be an enum of `nudge`, `focus-hint`, `reveal-answer`, `gentle-redirect`, `off-topic-redirect`, `celebrate-progress`, and `clarify-technique`, categorizing the pedagogical intent of the reply; this field is for logging and automated testing only and is never surfaced in the HTTP response returned to the frontend.
- [x] **SC-BE-029**: The system shall include `responseType` in the `COACH_RESPONSE` log line when a genuine parse succeeded; on the fallback path (no call to Bedrock's structured output reached a parseable reply), the system shall omit the `responseType` key entirely rather than logging a placeholder value.
- [x] **SC-BE-030**: Before injecting the deterministic hint's `nudge`, `focus`, or `reveal` text into the Bedrock prompt context, the system shall apply the same 0-indexed-to-1-indexed/named conversion that `HE-UI-001` through `HE-UI-004` define for the frontend's hint dialog (cell coordinates, single and multi-unit row/column references, block names), so the coach never receives or repeats a 0-indexed coordinate the player cannot map onto the 1-indexed board they see.

*(SC-BE-016, SC-BE-021, SC-BE-022, SC-BE-023 are retained as the safety net for Bedrock timeouts, SDK errors, and any residual response-parsing edge case; with SC-BE-025 in place their JSON-drift trigger frequency is expected to drop to ~zero, but the fallback path itself is unchanged.)*

## AI Provider Port + Vertex AI (Backend, GCP)

The coach's LLM call is placed behind a provider port so GCP can use its native model (Gemini via
Vertex AI, authenticated by the runtime service account) instead of calling AWS Bedrock cross-cloud.
The pedagogical contract (prompt, structured-output schema, fallback, token accounting) is identical
across providers; only the API surface + auth differ. @spec CP-GCP-090

- [x] **SC-GCP-001**: The active coach LLM client shall be selected at runtime behind a `CoachAiClient`
  port by the `coach.ai.provider` property — `BedrockCoachClient` (`bedrock`, the default) on AWS and
  `VertexCoachClient` (`vertex`, set via `%gcp.coach.ai.provider=vertex`) on GCP — mirroring the
  `GameRepository`/`CoachRateLimiter` producer pattern, so only the resolvable adapter's SDK client is
  instantiated.
- [x] **SC-GCP-002**: `VertexCoachClient` shall authenticate to Vertex AI via Application Default
  Credentials (the Cloud Run runtime service account) and use no long-lived keys, so the coach on GCP
  requires neither the cross-cloud AWS Bedrock access key nor its Secret Manager mount.
- [x] **SC-GCP-003**: `VertexCoachClient` shall call Gemini (model `coach.vertex.model-id`, region
  `coach.vertex.location`, project from `GCP_PROJECT_ID`) and constrain the reply to the same JSON
  schema as SC-BE-025 (`{aiMessage: string, revealHint: boolean, responseType: enum}`,
  `additionalProperties: false`) via Gemini structured output (`responseSchema` +
  `responseMimeType=application/json`), so a schema-violating reply cannot be returned regardless of
  prompt wording.
- [x] **SC-GCP-004**: Both adapters shall build an identical prompt — tutor system prompt, the
  human-readable board format (SC-BE-004), the escalation context block (SC-BE-024), and the trimmed
  conversation history — from a single provider-agnostic prompt builder, so the coaching content
  contract does not vary by provider.
- [x] **SC-GCP-005**: `VertexCoachClient` shall report tokens used from Gemini's `usageMetadata`
  (prompt + candidate tokens) to the monthly counter and `CoachRateLimiter`, consistent with the
  Bedrock adapter's accounting (SC-RL-002/005).
- [x] **SC-GCP-006**: On a Gemini error, or an unparseable or blank reply, `VertexCoachClient` shall
  take the same deterministic fallback as the Bedrock adapter (SC-BE-021/023) — returning the nudge
  text and logging `fallback: true` with a non-null `errorMsg` and the `rawResponseText`.
- [x] **SC-GCP-007**: When `coach.ai.provider=vertex`, the backend Cloud Run service shall not mount
  the AWS Bedrock secrets for the coach; the runtime service account shall hold `roles/aiplatform.user`
  and the project shall have `aiplatform.googleapis.com` enabled (provisioned by `gcp-bootstrap.sh`,
  per CP-GCP-083). *(Image recognition on GCP still calls Bedrock cross-cloud, so the AWS key is fully
  retired only once it too migrates — tracked separately.)*
- [x] **SC-GCP-008**: `VertexCoachClient` shall cache the tutor system prompt via a Vertex AI
  `CachedContent` resource, creating it once and reusing the same resource across calls until it
  nears its TTL — mirroring the cost intent of SC-BE-011 for the Vertex path, though the mechanism
  differs (Vertex requires an explicit, separately-managed cache resource; Bedrock's caching is
  automatic and implicit per call). On any failure to create or reuse the cached resource, the
  system shall proceed with an uncached call rather than failing the coaching request — caching is
  a cost optimization, not a functional requirement. *(Known, low-severity edge case: measured
  cache-creation latency is ~0-1s once a GCP project has created at least one cache before, but a
  project's very first-ever `CachedContent` creation took ~2 minutes in testing — a one-time
  per-project warm-up cost, not per-call. A brand-new GCP project's first live coach request could
  hit this before Cloud Run's 60s timeout. Synchronous creation was kept rather than adding
  background-job complexity, since this project is already past that one-time cost and Cloud Run's
  `cpu_idle=true`/scale-to-zero config makes reliable background execution impractical anyway; a
  fresh project should have one cache pre-warmed manually before its first real coach traffic.)*
- [x] **SC-GCP-009**: `VertexCoachClient` shall report cached-content tokens from Gemini's
  `usageMetadata` in the `COACH_RESPONSE` log line as `cacheReadTokens` (read from cache) and
  `cacheWriteTokens` (nonzero only on the call that creates or refreshes the cache), using the same
  field names as the Bedrock adapter (SC-BE-027) so cache effectiveness is comparable across
  providers in the coach-quality aggregate summary.

## Coach Response (Backend)

- [x] **SC-API-010**: The system shall return a `CoachResponse` containing `aiMessage`, the full `HintResponse` from the deterministic engine, a `revealHint` boolean, and `tokensUsedThisMonth` — the player's cumulative monthly token count after this call.
- [x] **SC-API-011**: The system shall always return the `HintResponse` fully populated regardless of `revealHint`; the frontend controls which fields to display.
- [x] **SC-API-012**: The system shall set `revealHint: true` only when the LLM's response explicitly states a specific cell coordinate and digit value as the solution.

## Widget Rendering (Frontend)

- [x] **SC-UI-001**: The system shall not render the coach button or mount the `CoachWidget` component on viewports below the `md` breakpoint (less than 900px wide).
- [x] **SC-UI-002**: When rendered on a desktop viewport, the coach button shall be visible at all times while a game is in progress.
- [x] **SC-UI-003**: When the coach panel is open, the Sudoku board, number pad, and toolbar shall remain fully visible and interactive.
- [x] **SC-UI-004**: The coach panel shall be positioned as a fixed overlay in the bottom-right corner of the viewport and shall not affect the layout of any other element.

## Panel Open and Close (Frontend)

- [x] **SC-UI-010**: When the coach button is clicked and the panel is closed, the system shall open the coach panel.
- [x] **SC-UI-011**: When the coach button is clicked and the panel is open, the system shall close the coach panel.
- [x] **SC-UI-012**: When the close button within the coach panel is clicked, the system shall close the coach panel.
- [x] **SC-UI-013**: When the Escape key is pressed and the coach panel is open, the system shall close the coach panel.
- [x] **SC-UI-014**: When the panel opens for the first time in a game session and no conversation history exists, the system shall display a welcome message without requiring the player to type anything. (Implementation note: a local canned message is used rather than an API call — avoids a Bedrock call on panel open.)

## Conversation Display (Frontend)

- [x] **SC-UI-020**: The system shall display user messages right-aligned with a distinct background colour.
- [x] **SC-UI-021**: The system shall display AI messages left-aligned with a distinct background colour different from user messages.
- [x] **SC-UI-022**: While a coaching API call is in flight, the system shall display an animated typing indicator in the AI message position.
- [x] **SC-UI-023**: While a coaching API call is in flight, the system shall disable the message input field and the send button.
- [x] **SC-UI-024**: While a coaching API call is in flight, the system shall hide the quick reply chips.
- [x] **SC-UI-025**: When a coaching API call completes, the system shall scroll the message list to show the new AI message.

## Quick Reply Chips (Frontend)

- [x] **SC-UI-030**: The system shall display quick reply chips below the message list when no API call is in flight.
- [x] **SC-UI-031**: When a quick reply chip is selected, the system shall send its preset message text to the coach API as the `userMessage` and append it to the conversation as a user message.
- [x] **SC-UI-032**: The system shall provide at minimum the following quick replies: "I'm stuck", "Tell me more", "Why does that work?".

## Board-Chat Linkage (Frontend)

- [x] **SC-UI-040**: When a coach response is received, the system shall immediately apply `hint.highlightCells` to the Sudoku board, replacing any previously active highlights.
- [x] **SC-UI-041**: While the coach panel is open, coach-sourced cell highlights shall take visual precedence over hint-sourced highlights.
- [x] **SC-UI-042**: When the coach panel is closed, the system shall clear all coach-sourced cell highlights from the board.

## Reveal Hint Handling (Frontend)

- [x] **SC-UI-050**: When `revealHint` is false in a coach response, the system shall not write `hint.solvedCells` into the current grid or remove `hint.eliminatedCandidates` from the candidate grid — only `hint.highlightCells` take effect, so the player is never shown a placed digit or struck-out candidate the coach's message didn't actually commit to.
- [x] **SC-UI-051**: When `revealHint` is true in a coach response, the system shall write `hint.solvedCells` into the current grid (clearing that cell's candidates) and remove `hint.eliminatedCandidates` from the candidate grid, matching the deterministic Hint button's reveal-stage behaviour (`useHintSystem.js`'s `advanceHint()`) — so a coach reveal and a Hint-button reveal leave the board in the same state.

## Conversation Lifecycle (Frontend)

- [x] **SC-UI-060**: The system shall preserve conversation history when the player makes a move on the board, so coaching context is maintained across moves.
- [x] **SC-UI-061**: When a new game starts, the system shall clear the conversation history.
- [x] **SC-UI-062**: When a new game starts, the system shall close the coach panel if it is open.
- [x] **SC-UI-063**: When the player closes and reopens the coach panel within the same game session, the system shall display the existing conversation history without firing a new welcome message.
- [x] **SC-UI-064**: Before sending a coaching request, the system shall trim the conversation history to the last 6 messages if it has grown beyond that limit.

## Rate Limiting and Cost Protection (Backend + Frontend)

- [x] **SC-RL-001**: When `aiCoachEnabled` is `false` in the player profile, `POST /ai/coach` shall return 403 with a JSON body containing `error: "AI Coach is disabled"`.
- [x] **SC-RL-002**: When the player's monthly token usage meets or exceeds `COACH_MONTHLY_TOKEN_LIMIT`, `POST /ai/coach` shall return 429 with a JSON body containing `tokensUsed`, `monthlyLimit`, and `resetsAt` (first day of next month).
- [x] **SC-RL-003**: When the player exceeds `COACH_RATE_LIMIT_PER_MINUTE` calls within the current UTC minute, `POST /ai/coach` shall return 429 with a `Retry-After` header.
- [x] **SC-RL-004**: All 429 responses from rate-limit enforcement shall include a `Retry-After` header indicating seconds until the next minute window.
- [x] **SC-RL-011**: The per-minute rate-limit counter store shall be selected at runtime by `sudoku.persistence` behind a `CoachRateLimiter` port — DynamoDB (`SudokuCoachRateLimits`, atomic conditional increment) on AWS, Firestore (`coachRateLimits`, transactional read-check-write, TTL on `expiresAt`) on GCP — and both adapters shall fail open (allow the call) on any storage error so infrastructure issues never block the user.
- [x] **SC-RL-005**: The monthly token counter shall reset to zero when the calendar month changes; the stored `coachTokenMonth` is compared to the current `YYYY-MM` value on each request.
- [x] **SC-RL-006**: `GET /players/me` shall return `aiCoachEnabled` (boolean) and `coachTokensUsedThisMonth` (number) in the player profile response; `PATCH /players/me` shall accept `aiCoachEnabled` as an optional boolean field.
- [x] **SC-RL-007**: The ProfileView shall display an AI Coach toggle switch and a read-only token usage counter showing tokens used vs the monthly limit.
- [x] **SC-RL-008**: The CoachWidget FAB shall not be rendered when `playerProfile.aiCoachEnabled` is `false`.
- [x] **SC-RL-009**: The CoachPanel header shall display the current token usage and monthly limit in white text on the dark header background, alongside a coin icon so the counted unit is legible without a tooltip.
- [x] **SC-RL-010**: The system shall update the CoachPanel's displayed token usage from `tokensUsedThisMonth` on each successful coach response, so the counter reflects the current session's usage without requiring a profile refetch.
