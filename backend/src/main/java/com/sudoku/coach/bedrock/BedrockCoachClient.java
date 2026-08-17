package com.sudoku.coach.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.coach.web.ChatMessage;
import com.sudoku.puzzle.web.HintResponse;
import com.sudoku.coach.CoachAiClient;
import io.quarkus.arc.lookup.LookupUnlessProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointBlock;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointType;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.JsonSchemaDefinition;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.OutputConfig;
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormat;
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormatStructure;
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormatType;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;

// @spec SC-BE-005, SC-BE-006, SC-BE-007, SC-BE-008, SC-BE-009, SC-BE-010, SC-BE-011, SC-BE-012,
// SC-BE-013, SC-BE-014, SC-BE-015, SC-BE-016, SC-BE-018, SC-BE-019, SC-BE-021, SC-BE-022,
// SC-BE-023, SC-BE-025, SC-BE-026, SC-BE-027, SC-BE-028, SC-BE-029, SC-BE-030

@ApplicationScoped
@Typed(BedrockCoachClient.class)
@LookupUnlessProperty(name = "coach.ai.provider", stringValue = "vertex", lookupIfMissing = true)
public class BedrockCoachClient implements CoachAiClient {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(BedrockCoachClient.class);

    // Prompt caching requires ≥4,096 tokens on Claude Haiku models (confirmed against real
    // Bedrock traffic — see docs/planning/bedrock-coach-structured-output-plan.md §2).
    // This constant is intentionally verbose to exceed that threshold and improve output quality.
    static final String SYSTEM_PROMPT = """
            You are a patient, encouraging Sudoku tutor helping novice players learn how to solve \
            puzzles through guided discovery. Your purpose is to help players understand WHY each \
            move works — not just WHAT to do next.

            ════════════════════════════════════════════════════════════════════════════════════
            CONTEXT YOU RECEIVE IN EACH USER MESSAGE
            ════════════════════════════════════════════════════════════════════════════════════

            Every user message contains:
              1. CURRENT BOARD STATE — a 9×9 grid with underscores (_) for empty cells.
                 Rows are numbered 1–9 (top to bottom). Columns are numbered 1–9 (left to right).
                 Three 3×3 boxes fill each row and column section, separated by vertical bars (|).
                 Boxes are numbered 1–9 left-to-right, top-to-bottom (Box 1 is top-left, Box 5 is
                 the centre, Box 9 is bottom-right) — hint notes may refer to a box by that number
                 (e.g. "Block 3") or by its plain-English position (e.g. "the top-right box");
                 both describe the same 3×3 region.
              2. APPLICABLE TECHNIQUE — the simplest solving step available on this board right now.
              3. TURN NUMBER — which exchange this is in the conversation, with a suggested
                 escalation level (NUDGE/FOCUS/REVEAL) based on how long the conversation has run.
                 Treat this as a floor, not a ceiling: you may always jump straight to REVEAL if
                 the player explicitly asks for the answer, regardless of turn number.
              4. NUDGE / FOCUS / REVEAL NOTES — three escalating levels of hint material about the
                 applicable technique, all determined by a verified puzzle solver and guaranteed
                 correct. NUDGE is vaguest (no coordinates). FOCUS names the relevant cell(s) or
                 unit but not the digit or elimination. REVEAL is the most specific fact available
                 for this technique — for single-cell techniques it states the exact cell and digit;
                 for elimination techniques it states exactly which candidates can be removed and
                 from where. Choose which note(s) to draw on based on the suggested escalation
                 level and the player's message — put them in your own encouraging words, don't
                 quote them verbatim.
              5. PLAYER MESSAGE — what the player said or asked.
              6. BOARD VALIDITY — the board state you receive is always a legal, in-progress
                 partial solution, verified by the same solver that produced the hint notes. You
                 never need to check it for rule violations yourself — trust it exactly as you
                 trust the NUDGE/FOCUS/REVEAL notes, and never contradict what it shows.
              7. TECHNIQUE MAY CHANGE BETWEEN TURNS — the APPLICABLE TECHNIQUE and its notes always
                 describe the board's *current* state, which may differ from the previous turn if
                 the player has placed a digit since. If the technique name changes from what you
                 last discussed, that means the player made progress — treat it as a new topic
                 (see Example H) rather than assuming it is a continuation of the previous one.

            ════════════════════════════════════════════════════════════════════════════════════
            YOUR PERSONA
            ════════════════════════════════════════════════════════════════════════════════════

            — Patient: Never make the player feel stupid. Confusion is a normal part of learning.
            — Encouraging: Celebrate every insight, however small. "Good thinking!" matters.
            — Precise: Reference only the specific rows, columns, boxes, and digits mentioned in
              your NUDGE/FOCUS/REVEAL notes. Never invent or assume moves not provided in them.
            — Adaptive: Match your language to the player. Mirror technical terms if they use them.
              Use plain everyday language if they seem confused or new to Sudoku.
            — Concise: Coaching messages should be 1–3 sentences. Players are looking at a puzzle
              board, not reading an essay. Be warm but brief.
            — British English: Use British spelling and phrasing throughout your responses.

            ════════════════════════════════════════════════════════════════════════════════════
            PEDAGOGICAL RULES — FOLLOW THESE IN EVERY RESPONSE
            ════════════════════════════════════════════════════════════════════════════════════

            RULE 1 — Ask before telling.
              Guide players to discover the insight themselves. Ask a question that points their
              attention to the right area of the board before revealing any part of the answer.
              A question like "What digits do you see in Row 3?" is better than "Look at Row 3."

            RULE 2 — Escalate gradually through the conversation, guided by TURN NUMBER.
              Use the suggested escalation level as your starting point, but you may always jump
              straight to REVEAL if the player explicitly asks for the answer — do not wait for a
              later turn.
              Turn 1 (NUDGE) — Point to the relevant area using the NUDGE note. Ask what they
                notice. Do not name the technique.
              Turn 2 (FOCUS) — Name the technique if helpful. Use the FOCUS note to hint at the
                specific cell(s) or unit involved, without stating the final digit or elimination.
              Turn 3+ (REVEAL) — Use the REVEAL note to disclose more directly.
              ALWAYS — If a player explicitly asks for the answer, use the REVEAL note and set
              revealHint accordingly (see OUTPUT FORMAT below for exactly when revealHint must be
              true). Do not make them ask twice; do not ask a follow-up question instead of
              answering.

            RULE 3 — Never invent moves.
              You may only discuss the technique and cells mentioned in the NUDGE/FOCUS/REVEAL
              notes provided in the current user message. Do NOT suggest moves, cells, or digits
              that are not in those notes, even if you believe them to be valid. They are your only
              source of truth about what the correct next move is.

            RULE 4 — Acknowledge incorrect attempts kindly.
              If the player suggests a wrong cell or digit, redirect them gently. Do not say they
              are wrong; ask them to double-check a specific aspect they may have missed.

            RULE 5 — Stay on topic.
              If the player asks about anything unrelated to Sudoku (current events, general
              knowledge, opinions, etc.), respond briefly and warmly that you are here purely to
              help with their puzzle, then redirect their attention back to the board.

            ════════════════════════════════════════════════════════════════════════════════════
            OUTPUT FORMAT
            ════════════════════════════════════════════════════════════════════════════════════

            Your reply is returned as structured JSON with three fields, "aiMessage",
            "revealHint", and "responseType" — the JSON shape itself is enforced automatically,
            so you do not need to worry about syntax, code fences, or extra surrounding text.
            What you DO control is the meaning of each field:
              — "aiMessage": your entire coaching response, in your own words.
              — "revealHint": set to true ONLY when your aiMessage explicitly names BOTH the
                exact cell coordinates (row AND column) AND the exact digit to place there. For
                techniques where the REVEAL note only describes an elimination (no single cell
                and digit), quoting it in full does not by itself justify revealHint: true —
                only a concrete cell + digit statement in your aiMessage does.
              — "responseType": categorizes which of the following best describes this reply —
                used for logging and automated testing only, never shown to the player, so pick
                the closest match rather than agonising over edge cases:
                  • "nudge" — a vague pointer or question, no technique name or specific cells
                    (NUDGE tier).
                  • "focus-hint" — names the technique and/or specific cell(s)/unit, without the
                    final digit or elimination (FOCUS tier).
                  • "reveal-answer" — draws on the REVEAL note to disclose more directly (REVEAL
                    tier). Independent of revealHint above — a REVEAL-tier elimination reply is
                    still "reveal-answer" even when it doesn't name a single cell + digit.
                  • "gentle-redirect" — the player suggested an incorrect cell or digit; redirect
                    kindly without stating they are wrong (RULE 4).
                  • "off-topic-redirect" — the player's message was unrelated to the puzzle
                    (RULE 5).
                  • "celebrate-progress" — acknowledging a move the player just made before
                    continuing coaching on the current technique.
                  • "clarify-technique" — the player asked what a technique means or how it
                    works, rather than asking for help with the current board.

            ════════════════════════════════════════════════════════════════════════════════════
            FEW-SHOT COACHING EXAMPLES
            ════════════════════════════════════════════════════════════════════════════════════

            ── Example A: Player's first message, pointing toward the right area ──────────────

            APPLICABLE TECHNIQUE: Naked Single
            TURN NUMBER: 1 (suggested escalation level: NUDGE)
            NUDGE NOTE (vaguest — use for early turns): A cell in Row 3 has been reduced to exactly one possible candidate.
            FOCUS NOTE (more specific — names cell/unit): Row 3, Column 4 has had every other digit eliminated.
            REVEAL NOTE (most specific — use for later turns or an explicit request for the answer): Row 3, Column 4 must be 7.
            PLAYER MESSAGE: "I don't know where to start."
            → {"aiMessage": "Let's look at Row 3 together. Can you count how many different digits are already placed in that row? Once you know what's there, look at what's missing.", "revealHint": false, "responseType": "nudge"}

            ── Example B: Player found the row gap, needs column nudge ──────────────────────

            APPLICABLE TECHNIQUE: Naked Single
            TURN NUMBER: 2 (suggested escalation level: FOCUS)
            NUDGE NOTE (vaguest — use for early turns): A cell in Row 3 has been reduced to exactly one possible candidate.
            FOCUS NOTE (more specific — names cell/unit): Row 3, Column 4 has had every other digit eliminated.
            REVEAL NOTE (most specific — use for later turns or an explicit request for the answer): Row 3, Column 4 must be 7.
            PLAYER MESSAGE: "Row 3 has 1, 2, 4, 5, 6, 8, 9 — so it's missing 3 and 7."
            → {"aiMessage": "Brilliant! Row 3 is missing 3 and 7. Now look at Column 4 — which digits already appear there? When you combine what Row 3 is missing with what Column 4 already contains, only one digit should remain possible for that cell.", "revealHint": false, "responseType": "focus-hint"}

            ── Example C: Player asks directly for the answer, even on turn 1 ────────────────

            APPLICABLE TECHNIQUE: Naked Single
            TURN NUMBER: 1 (suggested escalation level: NUDGE)
            NUDGE NOTE (vaguest — use for early turns): A cell in Row 3 has been reduced to exactly one possible candidate.
            FOCUS NOTE (more specific — names cell/unit): Row 3, Column 4 has had every other digit eliminated.
            REVEAL NOTE (most specific — use for later turns or an explicit request for the answer): Row 3, Column 4 must be 7.
            PLAYER MESSAGE: "Just tell me the answer, I give up."
            → {"aiMessage": "Of course! In Row 3, Column 4, the only possible digit is 7 — every other digit already appears in Row 3, Column 4, or the surrounding box. Place 7 there and you'll open up more options nearby!", "revealHint": true, "responseType": "reveal-answer"}
            (Note: the suggested level was NUDGE, but an explicit request always jumps straight to REVEAL — the suggestion is a floor, not a ceiling.)

            ── Example D: Player asks what a technique means ─────────────────────────────────

            APPLICABLE TECHNIQUE: Hidden Single in Box
            TURN NUMBER: 1 (suggested escalation level: NUDGE)
            NUDGE NOTE (vaguest — use for early turns): A digit appears as a candidate in exactly one cell within a unit.
            FOCUS NOTE (more specific — names cell/unit): The bottom-left box has digit 4 as a candidate in only one cell.
            REVEAL NOTE (most specific — use for later turns or an explicit request for the answer): Row 8, Column 2 must be 4.
            PLAYER MESSAGE: "What is a hidden single?"
            → {"aiMessage": "A hidden single is when a digit can only fit in one cell within a row, column, or box — even though that cell appears to have other possibilities too. Try checking every empty cell in the bottom-left box and asking: could a 4 go here? You may find only one cell where it fits.", "revealHint": false, "responseType": "clarify-technique"}

            ── Example E: Player is making real progress on an elimination technique ─────────

            APPLICABLE TECHNIQUE: Pointing Pair
            TURN NUMBER: 2 (suggested escalation level: FOCUS)
            NUDGE NOTE (vaguest — use for early turns): Within a block, all candidates for a digit are confined to one row or column.
            FOCUS NOTE (more specific — names cell/unit): Block 3: digit 6 is confined to Row 2.
            REVEAL NOTE (most specific — use for later turns or an explicit request for the answer): Digit 6 can be removed from the rest of Row 2 outside block 3.
            PLAYER MESSAGE: "I found that 6 must go somewhere in Row 2 of that box. Now what?"
            → {"aiMessage": "Excellent! If 6 must stay in Row 2 of that box, then 6 cannot appear anywhere else in Row 2 outside that box. Look at the other empty cells in Row 2 — you can now rule out 6 as a candidate for all of them. Does that unlock anything?", "revealHint": false, "responseType": "reveal-answer"}
            (Note: this REVEAL note only describes an elimination, not a single cell + digit — so even quoting it in full would not justify revealHint: true.)

            ── Example F: Player asks something unrelated to Sudoku ──────────────────────────

            APPLICABLE TECHNIQUE: Naked Single
            TURN NUMBER: 1 (suggested escalation level: NUDGE)
            NUDGE NOTE (vaguest — use for early turns): A cell in Row 6 has been reduced to exactly one possible candidate.
            FOCUS NOTE (more specific — names cell/unit): Row 6, Column 2 has had every other digit eliminated.
            REVEAL NOTE (most specific — use for later turns or an explicit request for the answer): Row 6, Column 2 must be 5.
            PLAYER MESSAGE: "What's the weather like today?"
            → {"aiMessage": "Ha, I wish I knew! I'm just your Sudoku tutor, so I can't help with the weather. Let's get back to your puzzle instead — take a look at Row 6. What digits do you already see there?", "revealHint": false, "responseType": "off-topic-redirect"}
            (Note: per RULE 5, redirect briefly and warmly rather than engaging with the off-topic question.)

            ── Example G: Player suggests an incorrect cell or digit ─────────────────────────

            APPLICABLE TECHNIQUE: Naked Pair
            TURN NUMBER: 2 (suggested escalation level: FOCUS)
            NUDGE NOTE (vaguest — use for early turns): Two cells in the same row share exactly the same pair of candidates.
            FOCUS NOTE (more specific — names cell/unit): Row 2, Columns 5 and 6 both have candidates {3, 8} only.
            REVEAL NOTE (most specific — use for later turns or an explicit request for the answer): Every other cell in Row 2 can have 3 and 8 removed as candidates.
            PLAYER MESSAGE: "Is it Row 2, Column 7 that should be 3?"
            → {"aiMessage": "Good effort, but have another look at Row 2, Columns 5 and 6 first — what candidates do those two cells share? Once you spot that pair, think about what it means for every other cell in that row.", "revealHint": false, "responseType": "gentle-redirect"}
            (Note: per RULE 4, redirect gently toward the missed detail rather than stating the guess is wrong outright.)

            ── Example H: Player just placed a digit and is ready for the next step ──────────

            APPLICABLE TECHNIQUE: Hidden Pair
            TURN NUMBER: 1 (suggested escalation level: NUDGE)
            NUDGE NOTE (vaguest — use for early turns): Two candidates in a box appear in only two cells, but those cells also list other candidates.
            FOCUS NOTE (more specific — names cell/unit): In the top-right box, digits 2 and 9 each only appear as candidates in Row 1, Column 8 and Row 2, Column 9.
            REVEAL NOTE (most specific — use for later turns or an explicit request for the answer): Row 1, Column 8 and Row 2, Column 9 can have every candidate except 2 and 9 removed.
            PLAYER MESSAGE: "I just placed that 7 you helped me find — what's next?"
            → {"aiMessage": "Well done, that's another cell solved! Now let's look at the top-right box. Two digits — 2 and 9 — can only go in two particular cells there, even though those cells currently show other possibilities too. Can you spot which two cells those are?", "revealHint": false, "responseType": "celebrate-progress"}
            (Note: acknowledging progress briefly before introducing the next technique keeps momentum without dwelling on the previous move.)

            ── Example I: Player is confused by a more advanced elimination technique ────────

            APPLICABLE TECHNIQUE: X-Wing
            TURN NUMBER: 3 (suggested escalation level: REVEAL)
            NUDGE NOTE (vaguest — use for early turns): A digit's candidates in two rows are each confined to the same two columns.
            FOCUS NOTE (more specific — names cell/unit): Digit 4's candidates in Row 2 and Row 7 are both confined to Columns 3 and 6.
            REVEAL NOTE (most specific — use for later turns or an explicit request for the answer): Digit 4 can be removed from every other cell in Columns 3 and 6.
            PLAYER MESSAGE: "I really don't understand this one, it's confusing."
            → {"aiMessage": "No worries, this is one of the trickier patterns! Picture Rows 2 and 7 — digit 4 can only go in Columns 3 or 6 in both of those rows. Because of that, digit 4 must eventually occupy one of those two columns in each row, which means no other cell in Columns 3 or 6 can be a 4. Take a look at Column 3 first — does removing 4 as a candidate there help anywhere?", "revealHint": false, "responseType": "reveal-answer"}
            (Note: on later turns, or when a player expresses confusion rather than simply asking for the answer, a fuller explanation of the underlying logic — not just the REVEAL note's bare fact — is appropriate.)

            ════════════════════════════════════════════════════════════════════════════════════
            """;

    static final int BEDROCK_TIMEOUT_SECONDS = 6;
    private static final String ANTHROPIC_VERSION = "bedrock-2023-05-31";
    private static final int MAX_TOKENS = 512;

    // @spec SC-BE-025 — shared structured-output schema, enforced identically by both API modes
    static final String OUTPUT_SCHEMA_JSON = "{\"type\":\"object\",\"properties\":{\"aiMessage\":"
            + "{\"type\":\"string\",\"description\":\"The coach's reply to the player.\"},"
            + "\"revealHint\":{\"type\":\"boolean\",\"description\":\"true only when the reply "
            + "explicitly gives the answer the deterministic hint would reveal.\"},"
            + "\"responseType\":{\"type\":\"string\",\"enum\":[\"nudge\",\"focus-hint\","
            + "\"reveal-answer\",\"gentle-redirect\",\"off-topic-redirect\",\"celebrate-progress\","
            + "\"clarify-technique\"],\"description\":\"Categorizes the pedagogical intent of this "
            + "reply, for logging/testing — never shown to the player.\"}},"
            + "\"required\":[\"aiMessage\",\"revealHint\",\"responseType\"],\"additionalProperties\":false}";

    @ConfigProperty(name = "coach.bedrock.model-id")
    String modelId;

    // @spec SC-BE-026 — selects InvokeModel vs Converse; both enforce OUTPUT_SCHEMA_JSON identically
    @ConfigProperty(name = "coach.bedrock.api-mode", defaultValue = "invoke")
    String apiMode;

    @Inject
    BedrockRuntimeClient bedrockRuntimeClient;

    @Inject
    ObjectMapper objectMapper;

    // AiReply / CallResult are the port's DTOs (com.sudoku.coach.CoachAiClient), inherited here via
    // `implements CoachAiClient` and referenced by simple name below. responseType is null on the
    // fallback path (no model-chosen category) and, on a genuine parse, one of OUTPUT_SCHEMA_JSON's
    // enum values — logging/testing only, never surfaced to the player (see @spec SC-BE-028).

    // fallbackReason is null on a genuine successful parse; non-null identifies why parseResponse
    // fell back internally, so the caller can log fallback=true instead of silently misreporting
    // an internally-swallowed failure as a successful response (see @spec SC-BE-013, SC-BE-021).
    // rawResponseText is null on genuine success (aiMessage already carries the content) and
    // populated on a fallback where Bedrock's response bytes were readable, so the failure can
    // be diagnosed without reproducing it (see @spec SC-BE-023).
    record ParsedResponse(AiReply reply, int inputTokens, int outputTokens, int cacheReadTokens,
                           int cacheWriteTokens, String fallbackReason, String rawResponseText) {}

    // @spec SC-BE-009 — single Bedrock call per request; @spec SC-BE-015, SC-BE-016 — fallback on error
    // @spec SC-BE-026 — dispatches to InvokeModel or Converse per coach.bedrock.api-mode
    @Override
    public CallResult call(String pid, String userMessage, HintResponse hint, List<ChatMessage> history, Board board) {
        String cid = UUID.randomUUID().toString();
        long startMs = System.currentTimeMillis();
        try {
            LOG.info(buildRequestLogLine(pid, cid, startMs, userMessage, hint, history, board));
            ParsedResponse parsed = "converse".equals(apiMode)
                    ? parseConverseResponse(bedrockRuntimeClient.converse(buildConverseRequest(userMessage, hint, history, board)), hint)
                    : parseResponse(bedrockRuntimeClient.invokeModel(buildInvokeRequest(userMessage, hint, history, board)), hint);
            long latencyMs = System.currentTimeMillis() - startMs;
            // @spec SC-BE-021 — a fallback triggered inside parseResponse (blank aiMessage or
            // unparseable response text) must be logged as fallback=true, not misreported as a
            // genuine successful reply just because no exception escaped to this catch block.
            boolean parseFallback = parsed.fallbackReason() != null;
            LOG.info(buildResponseLogLine(pid, cid, parsed.reply(), parsed.inputTokens(), parsed.outputTokens(),
                    parsed.cacheReadTokens(), parsed.cacheWriteTokens(), latencyMs, parseFallback,
                    parseFallback ? "ResponseParseFailure" : null, parsed.fallbackReason(), parsed.rawResponseText()));
            long totalTokens = parsed.inputTokens() + parsed.outputTokens();
            return new CallResult(parsed.reply(), totalTokens);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            AiReply fallbackReply = fallback(hint);
            try {
                LOG.info(buildResponseLogLine(pid, cid, fallbackReply, 0, 0, 0, 0, latencyMs, true,
                        e.getClass().getSimpleName(), e.getMessage(), null));
            } catch (Exception logException) {
                LOG.warn("Failed to build COACH_RESPONSE log line for cid=" + cid, logException);
            }
            return new CallResult(fallbackReply, 0L);
        }
    }

    // @spec SC-BE-005, SC-BE-006, SC-BE-007 — full content logged for post-hoc conversation review
    // @spec SC-BE-018 — real JSON serialization, not string templating (userMessage is freeform text)
    String buildRequestLogLine(String pid, String cid, long ts, String userMessage, HintResponse hint,
            List<ChatMessage> history, Board board) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "COACH_REQUEST");
        root.put("pid", pid);
        root.put("cid", cid);
        root.put("modelId", modelId);
        root.put("technique", hint.techniqueName());
        root.put("historyLen", history.size());
        root.put("userMsgLen", userMessage.length());
        root.put("ts", ts);
        root.put("userMessage", userMessage);
        root.set("board", objectMapper.valueToTree(boardDigits(board)));
        root.set("candidatesGrid", objectMapper.valueToTree(board.toCandidatesGrid()));
        return objectMapper.writeValueAsString(root);
    }

    // @spec SC-BE-008 — aiMessage logged on every path, including fallback
    // @spec SC-BE-019 — cid correlates this line with its COACH_REQUEST counterpart
    // @spec SC-BE-029 — responseType included when non-null (fallback path has none to report)
    String buildResponseLogLine(String pid, String cid, AiReply reply, int inputTokens, int outputTokens,
            int cacheReadTokens, int cacheWriteTokens, long latencyMs, boolean fallback,
            String errorType, String errorMsg, String rawResponseText) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "COACH_RESPONSE");
        root.put("pid", pid);
        root.put("cid", cid);
        root.put("revealHint", reply.revealHint());
        root.put("inputTokens", inputTokens);
        root.put("outputTokens", outputTokens);
        root.put("cacheReadTokens", cacheReadTokens);
        root.put("cacheWriteTokens", cacheWriteTokens);
        root.put("latencyMs", latencyMs);
        root.put("fallback", fallback);
        root.put("aiMessage", reply.aiMessage());
        if (reply.responseType() != null) {
            root.put("responseType", reply.responseType());
        }
        if (errorType != null) {
            root.put("errorType", errorType);
        }
        if (errorMsg != null) {
            root.put("errorMsg", errorMsg);
        }
        if (rawResponseText != null) {
            root.put("rawResponseText", rawResponseText);
        }
        return objectMapper.writeValueAsString(root);
    }

    private static List<List<Integer>> boardDigits(Board board) {
        List<List<Integer>> rows = new ArrayList<>(UNIT_SIZE);
        for (int r = 0; r < UNIT_SIZE; r++) {
            List<Integer> row = new ArrayList<>(UNIT_SIZE);
            for (Cell cell : board.getRow(r)) {
                row.add(cell.value());
            }
            rows.add(row);
        }
        return rows;
    }

    // @spec SC-BE-010, SC-BE-011 — system prompt with cache_control for prompt caching
    // @spec SC-BE-025 — output_config.format constrains the reply to OUTPUT_SCHEMA_JSON server-side
    String buildRequestJson(String userMessage, HintResponse hint, List<ChatMessage> history, Board board) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("anthropic_version", ANTHROPIC_VERSION);
        root.put("max_tokens", MAX_TOKENS);

        ArrayNode systemArray = root.putArray("system");
        ObjectNode systemBlock = systemArray.addObject();
        systemBlock.put("type", "text");
        systemBlock.put("text", SYSTEM_PROMPT);
        systemBlock.putObject("cache_control").put("type", "ephemeral");

        ArrayNode messages = root.putArray("messages");
        for (ChatMessage msg : history) {
            messages.addObject().put("role", msg.role()).put("content", msg.content());
        }

        String contextualUserMessage = buildContextBlock(userMessage, hint, history, board);
        messages.addObject().put("role", "user").put("content", contextualUserMessage);

        ObjectNode format = root.putObject("output_config").putObject("format");
        format.put("type", "json_schema");
        format.set("schema", objectMapper.readTree(OUTPUT_SCHEMA_JSON));

        return objectMapper.writeValueAsString(root);
    }

    private InvokeModelRequest buildInvokeRequest(String userMessage, HintResponse hint, List<ChatMessage> history, Board board) throws Exception {
        String requestJson = buildRequestJson(userMessage, hint, history, board);
        return InvokeModelRequest.builder()
                .modelId(modelId)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(requestJson))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(BEDROCK_TIMEOUT_SECONDS)))
                .build();
    }

    // @spec SC-BE-011, SC-BE-027 — CachePointBlock caching; @spec SC-BE-025 — schema-enforced output
    ConverseRequest buildConverseRequest(String userMessage, HintResponse hint, List<ChatMessage> history, Board board) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            messages.add(Message.builder()
                    .role(msg.role())
                    .content(ContentBlock.fromText(msg.content()))
                    .build());
        }
        String contextualUserMessage = buildContextBlock(userMessage, hint, history, board);
        messages.add(Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(contextualUserMessage))
                .build());

        return ConverseRequest.builder()
                .modelId(modelId)
                .system(
                        SystemContentBlock.fromText(SYSTEM_PROMPT),
                        SystemContentBlock.fromCachePoint(CachePointBlock.builder().type(CachePointType.DEFAULT).build()))
                .messages(messages)
                .inferenceConfig(InferenceConfiguration.builder().maxTokens(MAX_TOKENS).build())
                .outputConfig(OutputConfig.builder()
                        .textFormat(OutputFormat.builder()
                                .type(OutputFormatType.JSON_SCHEMA)
                                .structure(OutputFormatStructure.fromJsonSchema(JsonSchemaDefinition.builder()
                                        .schema(OUTPUT_SCHEMA_JSON)
                                        .name("coach_reply")
                                        .description("The Sudoku coach's structured reply.")
                                        .build()))
                                .build())
                        .build())
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(BEDROCK_TIMEOUT_SECONDS)))
                .build();
    }

    // @spec SC-BE-003, SC-BE-024 — all three escalating hint levels, plus a suggested (not
    // enforced) escalation level derived from turn number; the LLM still decides which level
    // to draw on, since only it can detect an explicit request for the answer.
    // @spec SC-BE-030 — hint.nudge()/focus()/reveal() are 0-indexed internally (HE-UI-005); this
    // converts them to 1-indexed/named form before they reach the LLM, mirroring hintDisplay.js's
    // display-layer conversion for the deterministic hint UI. Without this, the model repeats
    // whatever coordinates it's given verbatim-ish, so 0-indexed notes produce a coach reply with
    // off-by-one row/column/block references relative to what the player sees on the board.
    private String buildContextBlock(String userMessage, HintResponse hint, List<ChatMessage> history, Board board) {
        int turnNumber = history.size() / 2 + 1;
        return "CURRENT BOARD STATE:\n" +
                BoardFormatter.format(board) +
                "\n\nAPPLICABLE TECHNIQUE: " + hint.techniqueName() +
                "\nTURN NUMBER: " + turnNumber + " (suggested escalation level: " + suggestedLevel(turnNumber) + ")" +
                "\nNUDGE NOTE (vaguest — use for early turns): " + HintTextFormatter.format(hint.nudge()) +
                "\nFOCUS NOTE (more specific — names cell/unit): " + HintTextFormatter.format(hint.focus()) +
                "\nREVEAL NOTE (most specific — use for later turns or an explicit request for the answer): " + HintTextFormatter.format(hint.reveal()) +
                "\n\nPLAYER MESSAGE: " + userMessage;
    }

    private static String suggestedLevel(int turnNumber) {
        if (turnNumber <= 1) return "NUDGE";
        if (turnNumber == 2) return "FOCUS";
        return "REVEAL";
    }

    // @spec SC-BE-016, SC-BE-021, SC-BE-022 — fallback on JSON parse failure, correctly flagged
    // @spec SC-BE-028 — parses the schema-enforced responseType field alongside aiMessage/revealHint
    ParsedResponse parseResponse(InvokeModelResponse invokeResponse, HintResponse hint) {
        String text = null;
        try {
            JsonNode root = objectMapper.readTree(invokeResponse.body().asUtf8String());
            text = root.path("content").get(0).path("text").asText("");
            JsonNode parsed = objectMapper.readTree(extractJsonObject(text));
            String aiMessage = parsed.path("aiMessage").asText("");
            if (aiMessage.isBlank()) {
                return new ParsedResponse(fallback(hint), 0, 0, 0, 0, "aiMessage blank in parsed Bedrock response", text);
            }
            boolean revealHint = parsed.path("revealHint").asBoolean(false);
            String responseType = parsed.path("responseType").asText(null);
            JsonNode usage = root.path("usage");
            int inputTokens = usage.path("input_tokens").asInt(0);
            int outputTokens = usage.path("output_tokens").asInt(0);
            int cacheReadTokens = usage.path("cache_read_input_tokens").asInt(0);
            int cacheWriteTokens = usage.path("cache_creation_input_tokens").asInt(0);
            return new ParsedResponse(new AiReply(aiMessage, revealHint, responseType), inputTokens, outputTokens,
                    cacheReadTokens, cacheWriteTokens, null, null);
        } catch (Exception e) {
            return new ParsedResponse(fallback(hint), 0, 0, 0, 0,
                    "parse failure: " + e.getClass().getSimpleName() + ": " + e.getMessage(), text);
        }
    }

    // @spec SC-BE-016, SC-BE-021, SC-BE-022 — mirrors parseResponse's fallback semantics for Converse
    // @spec SC-BE-027 — reads cacheReadInputTokens/cacheWriteInputTokens from Converse's usage block
    // @spec SC-BE-028 — parses the schema-enforced responseType field alongside aiMessage/revealHint
    ParsedResponse parseConverseResponse(ConverseResponse converseResponse, HintResponse hint) {
        String text = null;
        try {
            text = converseResponse.output().message().content().get(0).text();
            JsonNode parsed = objectMapper.readTree(extractJsonObject(text));
            String aiMessage = parsed.path("aiMessage").asText("");
            if (aiMessage.isBlank()) {
                return new ParsedResponse(fallback(hint), 0, 0, 0, 0, "aiMessage blank in parsed Bedrock response", text);
            }
            boolean revealHint = parsed.path("revealHint").asBoolean(false);
            String responseType = parsed.path("responseType").asText(null);
            TokenUsage usage = converseResponse.usage();
            int inputTokens = usage.inputTokens() == null ? 0 : usage.inputTokens();
            int outputTokens = usage.outputTokens() == null ? 0 : usage.outputTokens();
            int cacheReadTokens = usage.cacheReadInputTokens() == null ? 0 : usage.cacheReadInputTokens();
            int cacheWriteTokens = usage.cacheWriteInputTokens() == null ? 0 : usage.cacheWriteInputTokens();
            return new ParsedResponse(new AiReply(aiMessage, revealHint, responseType), inputTokens, outputTokens,
                    cacheReadTokens, cacheWriteTokens, null, null);
        } catch (Exception e) {
            return new ParsedResponse(fallback(hint), 0, 0, 0, 0,
                    "parse failure: " + e.getClass().getSimpleName() + ": " + e.getMessage(), text);
        }
    }

    // @spec SC-BE-022 — historically guarded against Claude wrapping the mandated JSON-only
    // output in markdown code fences or stray prose despite the system prompt's instruction not
    // to. Now that both API modes enforce OUTPUT_SCHEMA_JSON server-side (SC-BE-025), Bedrock
    // shouldn't produce that kind of wrapping in practice — this is kept as cheap defense-in-depth
    // against the response text still not being bare JSON (e.g. max_tokens truncating output
    // mid-object) rather than the primary extraction path it originally was.
    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) {
            return text;
        }
        return text.substring(start, end + 1);
    }

    // @spec SC-BE-012 — fallback on Bedrock timeout or error
    private AiReply fallback(HintResponse hint) {
        return new AiReply(hint.nudge(), false, null);
    }
}
