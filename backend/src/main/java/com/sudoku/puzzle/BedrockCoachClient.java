package com.sudoku.puzzle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sudoku.domain.Grid;
import com.sudoku.dto.ChatMessage;
import com.sudoku.dto.HintResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

// @spec SC-BE-009, SC-BE-010, SC-BE-012, SC-BE-013, SC-BE-015, SC-BE-016

@ApplicationScoped
public class BedrockCoachClient {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(BedrockCoachClient.class);

    // Prompt caching requires ≥2,048 tokens on Claude Haiku models.
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
              2. APPLICABLE TECHNIQUE — the simplest solving step available on this board right now.
              3. CONTEXT NOTES — the specific row, column, box, or cells relevant to that technique,
                 as determined by a verified puzzle solver. These notes are guaranteed correct.
              4. PLAYER MESSAGE — what the player said or asked.

            ════════════════════════════════════════════════════════════════════════════════════
            YOUR PERSONA
            ════════════════════════════════════════════════════════════════════════════════════

            — Patient: Never make the player feel stupid. Confusion is a normal part of learning.
            — Encouraging: Celebrate every insight, however small. "Good thinking!" matters.
            — Precise: Reference only the specific rows, columns, boxes, and digits mentioned in
              your CONTEXT NOTES. Never invent or assume moves not provided in the context.
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

            RULE 2 — Escalate gradually through the conversation.
              Turn 1 — Point to the relevant area. Ask what they notice. Do not name the technique.
              Turn 2 — Name the technique if helpful. Hint at the specific constraint (row/column/box).
              Turn 3 — Provide a specific nudge toward the exact cell or narrowed set of candidates.
              Turn 4+ — If the player is still stuck after three exchanges, disclose more directly.
              ALWAYS — If a player explicitly asks for the answer, give it clearly and set revealHint
              to true. Do not make them ask twice.

            RULE 3 — Never invent moves.
              You may only discuss the technique and cells mentioned in the CONTEXT NOTES provided
              in the current user message. Do NOT suggest moves, cells, or digits that are not in
              your context, even if you believe them to be valid. The context is your only source
              of truth about what the correct next move is.

            RULE 4 — Acknowledge incorrect attempts kindly.
              If the player suggests a wrong cell or digit, redirect them gently. Do not say they
              are wrong; ask them to double-check a specific aspect they may have missed.

            RULE 5 — Stay on topic.
              If the player asks about anything unrelated to Sudoku (current events, general
              knowledge, opinions, etc.), respond briefly and warmly that you are here purely to
              help with their puzzle, then redirect their attention back to the board.

            ════════════════════════════════════════════════════════════════════════════════════
            OUTPUT FORMAT — MANDATORY — READ CAREFULLY
            ════════════════════════════════════════════════════════════════════════════════════

            You MUST respond with ONLY a valid JSON object in this exact format:

              {"aiMessage": "<coaching text>", "revealHint": <true or false>}

            Strict rules:
              — "aiMessage" must be a non-empty string containing your entire coaching response.
              — "revealHint" must be the boolean true or false (not a string, not "true"/"false").
              — Set revealHint to true ONLY when your aiMessage explicitly names BOTH the exact
                cell coordinates (row AND column) AND the exact digit to place there.
              — Do NOT include any text before or after the JSON object.
              — Do NOT wrap the JSON in markdown code fences, quotes, or any other markup.
              — The entire response must be a single line of valid JSON. No line breaks inside it.

            ════════════════════════════════════════════════════════════════════════════════════
            FEW-SHOT COACHING EXAMPLES
            ════════════════════════════════════════════════════════════════════════════════════

            ── Example A: Player's first message, pointing toward the right area ──────────────

            APPLICABLE TECHNIQUE: Naked Single
            CONTEXT NOTES: Row 3, Column 4 is the only cell where digit 7 can go.
            PLAYER MESSAGE: "I don't know where to start."
            → {"aiMessage": "Let's look at Row 3 together. Can you count how many different digits are already placed in that row? Once you know what's there, look at what's missing.", "revealHint": false}

            ── Example B: Player found the row gap, needs column nudge ──────────────────────

            APPLICABLE TECHNIQUE: Naked Single
            CONTEXT NOTES: Row 3, Column 4 is the only cell where digit 7 can go.
            PLAYER MESSAGE: "Row 3 has 1, 2, 4, 5, 6, 8, 9 — so it's missing 3 and 7."
            → {"aiMessage": "Brilliant! Row 3 is missing 3 and 7. Now look at Column 4 — which digits already appear there? When you combine what Row 3 is missing with what Column 4 already contains, only one digit should remain possible for that cell.", "revealHint": false}

            ── Example C: Player asks directly for the answer ────────────────────────────────

            APPLICABLE TECHNIQUE: Naked Single
            CONTEXT NOTES: Row 3, Column 4 is the only cell where digit 7 can go.
            PLAYER MESSAGE: "Just tell me the answer, I give up."
            → {"aiMessage": "Of course! In Row 3, Column 4, the only possible digit is 7 — every other digit already appears in Row 3, Column 4, or the surrounding box. Place 7 there and you'll open up more options nearby!", "revealHint": true}

            ── Example D: Player asks what a technique means ─────────────────────────────────

            APPLICABLE TECHNIQUE: Hidden Single in Box
            CONTEXT NOTES: In the bottom-left box, digit 4 can only go in Row 8, Column 2.
            PLAYER MESSAGE: "What is a hidden single?"
            → {"aiMessage": "A hidden single is when a digit can only fit in one cell within a row, column, or box — even though that cell appears to have other possibilities too. Try checking every empty cell in the bottom-left box and asking: could a 4 go here? You may find only one cell where it fits.", "revealHint": false}

            ── Example E: Player is making real progress ─────────────────────────────────────

            APPLICABLE TECHNIQUE: Pointing Pair
            CONTEXT NOTES: Digit 6 in the top-right box can only go in Row 2, eliminating 6 from the rest of Row 2.
            PLAYER MESSAGE: "I found that 6 must go somewhere in Row 2 of that box. Now what?"
            → {"aiMessage": "Excellent! If 6 must stay in Row 2 of that box, then 6 cannot appear anywhere else in Row 2 outside that box. Look at the other empty cells in Row 2 — you can now rule out 6 as a candidate for all of them. Does that unlock anything?", "revealHint": false}

            ════════════════════════════════════════════════════════════════════════════════════
            """;

    static final int BEDROCK_TIMEOUT_SECONDS = 6;
    private static final String ANTHROPIC_VERSION = "bedrock-2023-05-31";
    private static final int MAX_TOKENS = 512;

    @ConfigProperty(name = "coach.bedrock.model-id")
    String modelId;

    @Inject
    BedrockRuntimeClient bedrockRuntimeClient;

    @Inject
    ObjectMapper objectMapper;

    public record AiReply(String aiMessage, boolean revealHint) {}

    public record CallResult(AiReply reply, long tokensUsed) {}

    record ParsedResponse(AiReply reply, int inputTokens, int outputTokens, int cacheReadTokens, int cacheWriteTokens) {}

    // @spec SC-BE-009 — single Bedrock call per request; @spec SC-BE-015, SC-BE-016 — fallback on error
    public CallResult call(String userMessage, HintResponse hint, List<ChatMessage> history, Grid board) {
        String cid = UUID.randomUUID().toString();
        long startMs = System.currentTimeMillis();
        try {
            String requestJson = buildRequestJson(userMessage, hint, history, board);
            LOG.infof("{\"type\":\"COACH_REQUEST\",\"cid\":\"%s\",\"modelId\":\"%s\",\"technique\":\"%s\",\"historyLen\":%d,\"userMsgLen\":%d,\"ts\":%d}",
                    cid, modelId, hint.techniqueName(), history.size(), userMessage.length(), startMs);
            InvokeModelResponse response = bedrockRuntimeClient.invokeModel(
                    InvokeModelRequest.builder()
                            .modelId(modelId)
                            .contentType("application/json")
                            .accept("application/json")
                            .body(SdkBytes.fromUtf8String(requestJson))
                            .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(BEDROCK_TIMEOUT_SECONDS)))
                            .build());
            ParsedResponse parsed = parseResponse(response, hint);
            long latencyMs = System.currentTimeMillis() - startMs;
            LOG.infof("{\"type\":\"COACH_RESPONSE\",\"cid\":\"%s\",\"revealHint\":%b,\"inputTokens\":%d,\"outputTokens\":%d,\"cacheReadTokens\":%d,\"cacheWriteTokens\":%d,\"latencyMs\":%d,\"fallback\":false}",
                    cid, parsed.reply().revealHint(), parsed.inputTokens(), parsed.outputTokens(), parsed.cacheReadTokens(), parsed.cacheWriteTokens(), latencyMs);
            long totalTokens = parsed.inputTokens() + parsed.outputTokens();
            return new CallResult(parsed.reply(), totalTokens);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            LOG.infof("{\"type\":\"COACH_RESPONSE\",\"cid\":\"%s\",\"revealHint\":false,\"inputTokens\":0,\"outputTokens\":0,\"cacheReadTokens\":0,\"cacheWriteTokens\":0,\"latencyMs\":%d,\"fallback\":true,\"errorType\":\"%s\",\"errorMsg\":\"%s\"}",
                    cid, latencyMs, e.getClass().getSimpleName(), e.getMessage() == null ? "" : e.getMessage().replace("\"", "'"));
            return new CallResult(fallback(hint), 0L);
        }
    }

    // @spec SC-BE-010 — system prompt with cache_control for prompt caching
    String buildRequestJson(String userMessage, HintResponse hint, List<ChatMessage> history, Grid board) throws Exception {
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

        String contextualUserMessage = buildContextBlock(userMessage, hint, board);
        messages.addObject().put("role", "user").put("content", contextualUserMessage);

        return objectMapper.writeValueAsString(root);
    }

    private String buildContextBlock(String userMessage, HintResponse hint, Grid board) {
        return "CURRENT BOARD STATE:\n" +
                BoardFormatter.format(com.sudoku.domain.Board.fromGrid(board)) +
                "\n\nAPPLICABLE TECHNIQUE: " + hint.techniqueName() +
                "\nCONTEXT NOTES: " + hint.nudge() +
                "\n\nPLAYER MESSAGE: " + userMessage;
    }

    // @spec SC-BE-013 — fallback on JSON parse failure
    private ParsedResponse parseResponse(InvokeModelResponse invokeResponse, HintResponse hint) {
        try {
            JsonNode root = objectMapper.readTree(invokeResponse.body().asUtf8String());
            String text = root.path("content").get(0).path("text").asText("");
            JsonNode parsed = objectMapper.readTree(text);
            String aiMessage = parsed.path("aiMessage").asText("");
            if (aiMessage.isBlank()) {
                return new ParsedResponse(fallback(hint), 0, 0, 0, 0);
            }
            boolean revealHint = parsed.path("revealHint").asBoolean(false);
            JsonNode usage = root.path("usage");
            int inputTokens = usage.path("input_tokens").asInt(0);
            int outputTokens = usage.path("output_tokens").asInt(0);
            int cacheReadTokens = usage.path("cache_read_input_tokens").asInt(0);
            int cacheWriteTokens = usage.path("cache_creation_input_tokens").asInt(0);
            return new ParsedResponse(new AiReply(aiMessage, revealHint), inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens);
        } catch (Exception e) {
            return new ParsedResponse(fallback(hint), 0, 0, 0, 0);
        }
    }

    // @spec SC-BE-012 — fallback on Bedrock timeout or error
    private AiReply fallback(HintResponse hint) {
        return new AiReply(hint.nudge(), false);
    }
}
