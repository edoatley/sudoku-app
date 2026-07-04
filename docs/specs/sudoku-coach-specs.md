# Sudoku Coach — EARS Specifications

## Input Validation (Backend)

- [ ] **SC-API-001**: When `POST /puzzles/coach` is called without a valid Cognito JWT, the system shall return 401 (enforced by API Gateway before Lambda invocation).
- [ ] **SC-API-002**: When the board field is null, not a 9×9 array, or contains any integer outside 0–9, the system shall return 400.
- [ ] **SC-API-003**: When the userMessage field is null, blank, or exceeds 500 characters, the system shall return 400.
- [ ] **SC-API-004**: When the history array contains more than 6 messages, the system shall trim it to the last 6 messages rather than rejecting the request.

## Deterministic Pre-Analysis (Backend)

- [ ] **SC-BE-001**: When a coaching request is received, the system shall call the existing hint engine on the submitted board before making any call to Bedrock.
- [ ] **SC-BE-002**: When the hint engine finds no applicable strategy because the board is already solved, the system shall return 204 without calling Bedrock.
- [ ] **SC-BE-003**: When the hint engine returns a result, the system shall extract the technique name and relevant cells from that result and include them as context in the Bedrock prompt.
- [ ] **SC-BE-004**: The system shall format the board as a human-readable row-by-row string with box separators before injecting it into the Bedrock prompt, and shall not use `Arrays.deepToString` or any equivalent dense array format.

## Bedrock Integration (Backend)

- [ ] **SC-BE-010**: The system shall make exactly one Bedrock call per coaching request; it shall not use an agentic tool-calling loop.
- [ ] **SC-BE-011**: The system shall mark the tutor system prompt with `cache_control: {type: ephemeral}` so Bedrock can cache its KV state across requests within the 5-minute TTL window.
- [ ] **SC-BE-012**: The system shall ensure the system prompt is at least 1,024 tokens (the minimum Bedrock caching threshold), using inline few-shot coaching examples to reach this length if necessary.
- [ ] **SC-BE-013**: The system shall inject the conversation history (after trimming) and the player's current message into the Bedrock prompt in chronological order.
- [ ] **SC-BE-014**: The system shall instruct the LLM via the system prompt to return only valid JSON matching `{ "aiMessage": "...", "revealHint": true|false }` and no other text.
- [ ] **SC-BE-015**: When the Bedrock call times out or exceeds 6 seconds, the system shall return a 200 response using the deterministic hint's nudge text as `aiMessage` and `revealHint: false`.
- [ ] **SC-BE-016**: When the Bedrock response cannot be parsed as the expected JSON schema, the system shall fall back to the deterministic hint's nudge text as `aiMessage` and `revealHint: false`.
- [ ] **SC-BE-017**: The system shall never return a 5xx status code due to a Bedrock failure; all AI failures shall degrade to the nudge-text fallback and return 200.

## Coach Response (Backend)

- [ ] **SC-API-010**: The system shall return a `CoachResponse` containing `aiMessage`, the full `HintResponse` from the deterministic engine, and a `revealHint` boolean.
- [ ] **SC-API-011**: The system shall always return the `HintResponse` fully populated regardless of `revealHint`; the frontend controls which fields to display.
- [ ] **SC-API-012**: The system shall set `revealHint: true` only when the LLM's response explicitly states a specific cell coordinate and digit value as the solution.

## Widget Rendering (Frontend)

- [ ] **SC-UI-001**: The system shall not render the coach button or mount the `CoachWidget` component on viewports below the `md` breakpoint (less than 900px wide).
- [ ] **SC-UI-002**: When rendered on a desktop viewport, the coach button shall be visible at all times while a game is in progress.
- [ ] **SC-UI-003**: When the coach panel is open, the Sudoku board, number pad, and toolbar shall remain fully visible and interactive.
- [ ] **SC-UI-004**: The coach panel shall be positioned as a fixed overlay in the bottom-right corner of the viewport and shall not affect the layout of any other element.

## Panel Open and Close (Frontend)

- [ ] **SC-UI-010**: When the coach button is clicked and the panel is closed, the system shall open the coach panel.
- [ ] **SC-UI-011**: When the coach button is clicked and the panel is open, the system shall close the coach panel.
- [ ] **SC-UI-012**: When the close button within the coach panel is clicked, the system shall close the coach panel.
- [ ] **SC-UI-013**: When the Escape key is pressed and the coach panel is open, the system shall close the coach panel.
- [ ] **SC-UI-014**: When the panel opens for the first time in a game session and no conversation history exists, the system shall automatically send a welcome message to the coach API without requiring the player to type anything.

## Conversation Display (Frontend)

- [ ] **SC-UI-020**: The system shall display user messages right-aligned with a distinct background colour.
- [ ] **SC-UI-021**: The system shall display AI messages left-aligned with a distinct background colour different from user messages.
- [ ] **SC-UI-022**: While a coaching API call is in flight, the system shall display an animated typing indicator in the AI message position.
- [ ] **SC-UI-023**: While a coaching API call is in flight, the system shall disable the message input field and the send button.
- [ ] **SC-UI-024**: While a coaching API call is in flight, the system shall hide the quick reply chips.
- [ ] **SC-UI-025**: When a coaching API call completes, the system shall scroll the message list to show the new AI message.

## Quick Reply Chips (Frontend)

- [ ] **SC-UI-030**: The system shall display quick reply chips below the message list when no API call is in flight.
- [ ] **SC-UI-031**: When a quick reply chip is selected, the system shall send its preset message text to the coach API as the `userMessage` and append it to the conversation as a user message.
- [ ] **SC-UI-032**: The system shall provide at minimum the following quick replies: "I'm stuck", "Tell me more", "Why does that work?".

## Board-Chat Linkage (Frontend)

- [ ] **SC-UI-040**: When a coach response is received, the system shall immediately apply `hint.highlightCells` to the Sudoku board, replacing any previously active highlights.
- [ ] **SC-UI-041**: While the coach panel is open, coach-sourced cell highlights shall take visual precedence over hint-sourced highlights.
- [ ] **SC-UI-042**: When the coach panel is closed, the system shall clear all coach-sourced cell highlights from the board.

## Reveal Hint Handling (Frontend)

- [ ] **SC-UI-050**: When `revealHint` is false in a coach response, the system shall not display `hint.reveal`, `hint.solvedCells`, or `hint.eliminatedCandidates` to the player.
- [ ] **SC-UI-051**: When `revealHint` is true in a coach response, the system shall make all hint fields available for display alongside the AI coaching message.

## Conversation Lifecycle (Frontend)

- [ ] **SC-UI-060**: The system shall preserve conversation history when the player makes a move on the board, so coaching context is maintained across moves.
- [ ] **SC-UI-061**: When a new game starts, the system shall clear the conversation history.
- [ ] **SC-UI-062**: When a new game starts, the system shall close the coach panel if it is open.
- [ ] **SC-UI-063**: When the player closes and reopens the coach panel within the same game session, the system shall display the existing conversation history without firing a new welcome message.
- [ ] **SC-UI-064**: Before sending a coaching request, the system shall trim the conversation history to the last 6 messages if it has grown beyond that limit.
