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

    static final int BEDROCK_TIMEOUT_SECONDS = 6;
    private static final String ANTHROPIC_VERSION = "bedrock-2023-05-31";
    private static final int MAX_TOKENS = 512;

    // @spec SC-BE-025 — shared structured-output schema, enforced identically by both API modes

    @ConfigProperty(name = "coach.bedrock.model-id")
    String modelId;

    // @spec SC-BE-026 — selects InvokeModel vs Converse; both enforce CoachPromptBuilder.OUTPUT_SCHEMA_JSON identically
    @ConfigProperty(name = "coach.bedrock.api-mode", defaultValue = "invoke")
    String apiMode;

    @Inject
    BedrockRuntimeClient bedrockRuntimeClient;

    @Inject
    ObjectMapper objectMapper;

    // AiReply / CallResult are the port's DTOs (com.sudoku.coach.CoachAiClient), inherited here via
    // `implements CoachAiClient` and referenced by simple name below. responseType is null on the
    // fallback path (no model-chosen category) and, on a genuine parse, one of CoachPromptBuilder.OUTPUT_SCHEMA_JSON's
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
    // @spec SC-BE-025 — output_config.format constrains the reply to CoachPromptBuilder.OUTPUT_SCHEMA_JSON server-side
    String buildRequestJson(String userMessage, HintResponse hint, List<ChatMessage> history, Board board) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("anthropic_version", ANTHROPIC_VERSION);
        root.put("max_tokens", MAX_TOKENS);

        ArrayNode systemArray = root.putArray("system");
        ObjectNode systemBlock = systemArray.addObject();
        systemBlock.put("type", "text");
        systemBlock.put("text", CoachPromptBuilder.SYSTEM_PROMPT);
        systemBlock.putObject("cache_control").put("type", "ephemeral");

        ArrayNode messages = root.putArray("messages");
        for (ChatMessage msg : history) {
            messages.addObject().put("role", msg.role()).put("content", msg.content());
        }

        String contextualUserMessage = CoachPromptBuilder.buildContextBlock(userMessage, hint, history, board);
        messages.addObject().put("role", "user").put("content", contextualUserMessage);

        ObjectNode format = root.putObject("output_config").putObject("format");
        format.put("type", "json_schema");
        format.set("schema", objectMapper.readTree(CoachPromptBuilder.OUTPUT_SCHEMA_JSON));

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
        String contextualUserMessage = CoachPromptBuilder.buildContextBlock(userMessage, hint, history, board);
        messages.add(Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(contextualUserMessage))
                .build());

        return ConverseRequest.builder()
                .modelId(modelId)
                .system(
                        SystemContentBlock.fromText(CoachPromptBuilder.SYSTEM_PROMPT),
                        SystemContentBlock.fromCachePoint(CachePointBlock.builder().type(CachePointType.DEFAULT).build()))
                .messages(messages)
                .inferenceConfig(InferenceConfiguration.builder().maxTokens(MAX_TOKENS).build())
                .outputConfig(OutputConfig.builder()
                        .textFormat(OutputFormat.builder()
                                .type(OutputFormatType.JSON_SCHEMA)
                                .structure(OutputFormatStructure.fromJsonSchema(JsonSchemaDefinition.builder()
                                        .schema(CoachPromptBuilder.OUTPUT_SCHEMA_JSON)
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
    // to. Now that both API modes enforce CoachPromptBuilder.OUTPUT_SCHEMA_JSON server-side (SC-BE-025), Bedrock
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
