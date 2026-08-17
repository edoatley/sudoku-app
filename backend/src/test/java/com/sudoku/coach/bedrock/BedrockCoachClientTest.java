package com.sudoku.coach.bedrock;

import com.sudoku.coach.CoachAiClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudoku.domain.Board;
import com.sudoku.domain.Grid;
import com.sudoku.coach.web.ChatMessage;
import com.sudoku.puzzle.web.HintResponse;
import com.sudoku.puzzle.hint.Difficulty;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// @spec SC-BE-003, SC-BE-005, SC-BE-006, SC-BE-007, SC-BE-008, SC-BE-009, SC-BE-012, SC-BE-013,
// SC-BE-015, SC-BE-016, SC-BE-018, SC-BE-019, SC-BE-021, SC-BE-022, SC-BE-023, SC-BE-024,
// SC-BE-025, SC-BE-026, SC-BE-027
@QuarkusTest
class BedrockCoachClientTest {

    @InjectMock
    BedrockRuntimeClient bedrockRuntimeClient;

    @Inject
    BedrockCoachClient bedrockCoachClient;

    @Inject
    ObjectMapper objectMapper;

    private static final Grid GRID = Grid.of(List.of(
            List.of(5, 3, 0, 0, 7, 0, 0, 0, 0),
            List.of(6, 0, 0, 1, 9, 5, 0, 0, 0),
            List.of(0, 9, 8, 0, 0, 0, 0, 6, 0),
            List.of(8, 0, 0, 0, 6, 0, 0, 0, 3),
            List.of(4, 0, 0, 8, 0, 3, 0, 0, 1),
            List.of(7, 0, 0, 0, 2, 0, 0, 0, 6),
            List.of(0, 6, 0, 0, 0, 0, 2, 8, 0),
            List.of(0, 0, 0, 4, 1, 9, 0, 0, 5),
            List.of(0, 0, 0, 0, 8, 0, 0, 7, 9)
    ));

    /** A fresh candidates-populated {@link Board} per call — mirrors what {@code SudokuCoachServiceImpl} builds. */
    private static Board board() {
        Board board = Board.fromGrid(GRID);
        board.calculateAllCandidates();
        return board;
    }

    private static final HintResponse HINT = new HintResponse(
            "Naked Single", "naked-single", Difficulty.EASY, 1,
            "Only one digit can go in Row 1, Column 3.", "Look at Row 1.",
            "Place 4 in Row 1, Column 3.", List.of(), List.of(), List.of(), List.of());

    @AfterEach
    void resetApiMode() {
        // @spec SC-BE-026 — tests that flip api-mode to "converse" must not leak into other tests
        ClientProxy.unwrap(bedrockCoachClient).apiMode = "invoke";
    }

    @Test
    void call_validBedrockResponse_returnsParsedAiReply() {
        stubBedrockResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"Great question! Let's look at Row 1 together.\\",\\"revealHint\\":false}"}]}
                """);

        CoachAiClient.AiReply reply = bedrockCoachClient.call("game-1", "I'm stuck", HINT, List.of(), board()).reply();

        assertEquals("Great question! Let's look at Row 1 together.", reply.aiMessage());
        assertFalse(reply.revealHint());
    }

    @Test
    void call_revealHintTrue_propagatesFlag() {
        stubBedrockResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"Place 4 in Row 1, Column 3.\\",\\"revealHint\\":true}"}]}
                """);

        CoachAiClient.AiReply reply = bedrockCoachClient.call("game-1", "Just tell me", HINT, List.of(), board()).reply();

        assertTrue(reply.revealHint());
    }

    @Test
    void call_invalidJsonInResponseText_fallsBackToNudge() {
        // @spec SC-BE-013
        stubBedrockResponse("""
                {"content":[{"type":"text","text":"This is not JSON at all"}]}
                """);

        CoachAiClient.AiReply reply = bedrockCoachClient.call("game-1", "Help", HINT, List.of(), board()).reply();

        assertEquals(HINT.nudge(), reply.aiMessage());
        assertFalse(reply.revealHint());
    }

    @Test
    void call_blankAiMessageInResponse_fallsBackToNudge() {
        // @spec SC-BE-013
        stubBedrockResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"\\",\\"revealHint\\":false}"}]}
                """);

        CoachAiClient.AiReply reply = bedrockCoachClient.call("game-1", "Help", HINT, List.of(), board()).reply();

        assertEquals(HINT.nudge(), reply.aiMessage());
    }

    @Test
    void parseResponse_extractsJsonWrappedInMarkdownFences_andDoesNotFallBack() {
        // @spec SC-BE-022 — Claude sometimes wraps the mandated JSON-only output in code fences
        InvokeModelResponse response = invokeModelResponse("""
                {"content":[{"type":"text","text":"```json\\n{\\"aiMessage\\":\\"Great question!\\",\\"revealHint\\":false}\\n```"}]}
                """);

        BedrockCoachClient.ParsedResponse parsed = bedrockCoachClient.parseResponse(response, HINT);

        assertNull(parsed.fallbackReason());
        assertEquals("Great question!", parsed.reply().aiMessage());
    }

    @Test
    void parseResponse_setsFallbackReason_whenTextIsNotJson() {
        // @spec SC-BE-021 — must be flagged even though invokeModel() itself did not throw
        InvokeModelResponse response = invokeModelResponse("""
                {"content":[{"type":"text","text":"This is not JSON at all"}]}
                """);

        BedrockCoachClient.ParsedResponse parsed = bedrockCoachClient.parseResponse(response, HINT);

        assertNotNull(parsed.fallbackReason());
        assertEquals(HINT.nudge(), parsed.reply().aiMessage());
    }

    @Test
    void parseResponse_capturesRawResponseText_whenTextIsNotJson() {
        // @spec SC-BE-023 — raw model text preserved for diagnosis when it isn't parseable at all
        InvokeModelResponse response = invokeModelResponse("""
                {"content":[{"type":"text","text":"This is not JSON at all"}]}
                """);

        BedrockCoachClient.ParsedResponse parsed = bedrockCoachClient.parseResponse(response, HINT);

        assertEquals("This is not JSON at all", parsed.rawResponseText());
    }

    @Test
    void parseResponse_setsFallbackReason_whenAiMessageBlank() {
        // @spec SC-BE-021
        InvokeModelResponse response = invokeModelResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"\\",\\"revealHint\\":false}"}]}
                """);

        BedrockCoachClient.ParsedResponse parsed = bedrockCoachClient.parseResponse(response, HINT);

        assertNotNull(parsed.fallbackReason());
    }

    @Test
    void parseResponse_capturesRawResponseText_whenAiMessageBlank() {
        // @spec SC-BE-023
        InvokeModelResponse response = invokeModelResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"\\",\\"revealHint\\":false}"}]}
                """);

        BedrockCoachClient.ParsedResponse parsed = bedrockCoachClient.parseResponse(response, HINT);

        assertEquals("{\"aiMessage\":\"\",\"revealHint\":false}", parsed.rawResponseText());
    }

    @Test
    void parseResponse_fallbackReasonIsNull_onGenuineSuccess() {
        InvokeModelResponse response = invokeModelResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"Great!\\",\\"revealHint\\":false}"}]}
                """);

        BedrockCoachClient.ParsedResponse parsed = bedrockCoachClient.parseResponse(response, HINT);

        assertNull(parsed.fallbackReason());
    }

    @Test
    void parseResponse_mapsResponseTypeFromParsedJson() {
        // @spec SC-BE-028
        InvokeModelResponse response = invokeModelResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"Great!\\",\\"revealHint\\":false,\\"responseType\\":\\"nudge\\"}"}]}
                """);

        BedrockCoachClient.ParsedResponse parsed = bedrockCoachClient.parseResponse(response, HINT);

        assertEquals("nudge", parsed.reply().responseType());
    }

    @Test
    void parseResponse_responseTypeIsNull_onFallback() {
        // @spec SC-BE-028, SC-BE-029 — fallback never calls Bedrock's structured output, so there
        // is no model-chosen category to report
        InvokeModelResponse response = invokeModelResponse("""
                {"content":[{"type":"text","text":"This is not JSON at all"}]}
                """);

        BedrockCoachClient.ParsedResponse parsed = bedrockCoachClient.parseResponse(response, HINT);

        assertNull(parsed.reply().responseType());
    }

    @Test
    void parseResponse_rawResponseTextIsNull_onGenuineSuccess() {
        // @spec SC-BE-023 — aiMessage already carries the useful content on success; no duplication
        InvokeModelResponse response = invokeModelResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"Great!\\",\\"revealHint\\":false}"}]}
                """);

        BedrockCoachClient.ParsedResponse parsed = bedrockCoachClient.parseResponse(response, HINT);

        assertNull(parsed.rawResponseText());
    }

    @Test
    void call_sdkException_fallsBackToNudge() {
        // @spec SC-BE-012 — fallback on Bedrock timeout or SDK error
        when(bedrockRuntimeClient.invokeModel(any(InvokeModelRequest.class)))
                .thenThrow(SdkClientException.builder().message("connection refused").build());

        CoachAiClient.AiReply reply = bedrockCoachClient.call("game-1", "Help", HINT, List.of(), board()).reply();

        assertEquals(HINT.nudge(), reply.aiMessage());
        assertFalse(reply.revealHint());
    }

    @Test
    void call_withHistory_includesHistoryInRequest() throws Exception {
        stubBedrockResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"Good thinking!\\",\\"revealHint\\":false}"}]}
                """);
        List<ChatMessage> history = List.of(
                new ChatMessage("user", "I see digits 1 and 2 in Row 1"),
                new ChatMessage("assistant", "{\"aiMessage\": \"Good start!\", \"revealHint\": false}"));

        String requestJson = bedrockCoachClient.buildRequestJson("Tell me more", HINT, history, board());

        assertTrue(requestJson.contains("I see digits 1 and 2 in Row 1"));
        assertTrue(requestJson.contains("Good start!"));
    }

    @Test
    void buildRequestJson_includesCacheControlOnSystemPrompt() throws Exception {
        String requestJson = bedrockCoachClient.buildRequestJson("Help", HINT, List.of(), board());

        assertTrue(requestJson.contains("cache_control"));
        assertTrue(requestJson.contains("ephemeral"));
    }

    @Test
    void buildRequestJson_includesOutputConfigJsonSchema() throws Exception {
        // @spec SC-BE-025 — invoke mode enforces the shared schema via output_config.format
        String requestJson = bedrockCoachClient.buildRequestJson("Help", HINT, List.of(), board());

        JsonNode root = objectMapper.readTree(requestJson);
        JsonNode format = root.path("output_config").path("format");
        assertEquals("json_schema", format.path("type").asText());
        assertEquals("object", format.path("schema").path("type").asText());
        JsonNode required = format.path("schema").path("required");
        assertTrue(required.toString().contains("aiMessage"));
        assertTrue(required.toString().contains("revealHint"));
        assertTrue(required.toString().contains("responseType"));
        assertFalse(format.path("schema").path("additionalProperties").asBoolean(true));
    }

    @Test
    void outputSchema_constrainsResponseTypeToTheDocumentedEnum() throws Exception {
        // @spec SC-BE-028 — responseType is enum-constrained, not free-form, in both API modes
        JsonNode schema = objectMapper.readTree(BedrockCoachClient.OUTPUT_SCHEMA_JSON);
        JsonNode enumValues = schema.path("properties").path("responseType").path("enum");

        List<String> values = new ArrayList<>();
        enumValues.forEach(v -> values.add(v.asText()));

        assertEquals(List.of("nudge", "focus-hint", "reveal-answer", "gentle-redirect",
                "off-topic-redirect", "celebrate-progress", "clarify-technique"), values);
    }

    // ---- converse mode (SC-BE-025, SC-BE-026, SC-BE-027) ----

    @Test
    void buildConverseRequest_appendsCachePointToSystemBlocks() {
        ConverseRequest request = bedrockCoachClient.buildConverseRequest("Help", HINT, List.of(), board());

        List<SystemContentBlock> system = request.system();
        assertTrue(system.stream().anyMatch(b -> b.text() != null && b.text().equals(BedrockCoachClient.SYSTEM_PROMPT)));
        assertTrue(system.stream().anyMatch(b -> b.cachePoint() != null));
    }

    @Test
    void buildConverseRequest_setsSchemaEnforcedOutputConfig() {
        ConverseRequest request = bedrockCoachClient.buildConverseRequest("Help", HINT, List.of(), board());

        String schema = request.outputConfig().textFormat().structure().jsonSchema().schema();
        assertEquals(BedrockCoachClient.OUTPUT_SCHEMA_JSON, schema);
        assertEquals("json_schema", request.outputConfig().textFormat().typeAsString());
        assertTrue(schema.contains("responseType"));
    }

    @Test
    void buildConverseRequest_includesHistoryAndUserMessageAsMessages() {
        List<ChatMessage> history = List.of(
                new ChatMessage("user", "I see digits 1 and 2 in Row 1"),
                new ChatMessage("assistant", "{\"aiMessage\": \"Good start!\", \"revealHint\": false}"));

        ConverseRequest request = bedrockCoachClient.buildConverseRequest("Tell me more", HINT, history, board());

        assertEquals(3, request.messages().size());
        assertEquals(ConversationRole.USER, request.messages().get(2).role());
        assertTrue(request.messages().get(2).content().get(0).text().contains("Tell me more"));
    }

    @Test
    void parseConverseResponse_validResponse_returnsParsedAiReply() {
        ConverseResponse response = converseResponse(
                "{\"aiMessage\":\"Great question! Let's look at Row 1 together.\",\"revealHint\":false,\"responseType\":\"nudge\"}",
                4300, 42, 0, 4300);

        BedrockCoachClient.ParsedResponse parsed = bedrockCoachClient.parseConverseResponse(response, HINT);

        assertEquals("Great question! Let's look at Row 1 together.", parsed.reply().aiMessage());
        assertFalse(parsed.reply().revealHint());
        assertEquals("nudge", parsed.reply().responseType());
        assertNull(parsed.fallbackReason());
    }

    @Test
    void parseConverseResponse_responseTypeIsNull_onFallback() {
        // @spec SC-BE-028, SC-BE-029
        ConverseResponse response = converseResponse("This is not JSON at all", 10, 5, 0, 0);

        BedrockCoachClient.ParsedResponse parsed = bedrockCoachClient.parseConverseResponse(response, HINT);

        assertNull(parsed.reply().responseType());
    }

    @Test
    void parseConverseResponse_mapsCacheReadAndWriteTokensFromUsage() {
        // @spec SC-BE-027 — cacheReadInputTokens/cacheWriteInputTokens surfaced from Converse's usage block
        ConverseResponse response = converseResponse(
                "{\"aiMessage\":\"Great!\",\"revealHint\":false}", 42, 10, 4300, 0);

        BedrockCoachClient.ParsedResponse parsed = bedrockCoachClient.parseConverseResponse(response, HINT);

        assertEquals(42, parsed.inputTokens());
        assertEquals(10, parsed.outputTokens());
        assertEquals(4300, parsed.cacheReadTokens());
        assertEquals(0, parsed.cacheWriteTokens());
    }

    @Test
    void parseConverseResponse_invalidJson_fallsBackToNudge() {
        ConverseResponse response = converseResponse("This is not JSON at all", 10, 5, 0, 0);

        BedrockCoachClient.ParsedResponse parsed = bedrockCoachClient.parseConverseResponse(response, HINT);

        assertEquals(HINT.nudge(), parsed.reply().aiMessage());
        assertNotNull(parsed.fallbackReason());
        assertEquals("This is not JSON at all", parsed.rawResponseText());
    }

    @Test
    void parseConverseResponse_blankAiMessage_fallsBackToNudge() {
        ConverseResponse response = converseResponse("{\"aiMessage\":\"\",\"revealHint\":false}", 10, 5, 0, 0);

        BedrockCoachClient.ParsedResponse parsed = bedrockCoachClient.parseConverseResponse(response, HINT);

        assertEquals(HINT.nudge(), parsed.reply().aiMessage());
        assertNotNull(parsed.fallbackReason());
    }

    @Test
    void call_apiModeConverse_dispatchesToConverseAndReturnsParsedReply() {
        // @spec SC-BE-026 — coach.bedrock.api-mode=converse routes through Converse, not InvokeModel
        ClientProxy.unwrap(bedrockCoachClient).apiMode = "converse";
        ConverseResponse response = converseResponse(
                "{\"aiMessage\":\"Good thinking!\",\"revealHint\":false}", 4300, 40, 0, 4300);
        when(bedrockRuntimeClient.converse(any(ConverseRequest.class))).thenReturn(response);

        BedrockCoachClient.CallResult result = bedrockCoachClient.call("game-1", "I'm stuck", HINT, List.of(), board());

        assertEquals("Good thinking!", result.reply().aiMessage());
        assertEquals(4340L, result.tokensUsed());
    }

    @Test
    void call_apiModeConverse_sdkException_fallsBackToNudge() {
        // @spec SC-BE-015, SC-BE-017 — fallback behaviour is identical regardless of api-mode
        ClientProxy.unwrap(bedrockCoachClient).apiMode = "converse";
        when(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .thenThrow(SdkClientException.builder().message("connection refused").build());

        CoachAiClient.AiReply reply = bedrockCoachClient.call("game-1", "Help", HINT, List.of(), board()).reply();

        assertEquals(HINT.nudge(), reply.aiMessage());
        assertFalse(reply.revealHint());
    }

    @Test
    void buildRequestJson_includesBoardAndTechnique() throws Exception {
        String requestJson = bedrockCoachClient.buildRequestJson("Help", HINT, List.of(), board());

        assertTrue(requestJson.contains("CURRENT BOARD STATE"));
        assertTrue(requestJson.contains(HINT.techniqueName()));
        // @spec SC-BE-030 — HINT.nudge() is 0-indexed ("Row 1, Column 3"); the request must carry
        // the 1-indexed conversion ("Row 2, Column 4"), not the raw fixture text verbatim
        assertTrue(requestJson.contains("Only one digit can go in Row 2, Column 4."));
    }

    @Test
    void buildRequestJson_includesAllThreeHintLevels() throws Exception {
        // @spec SC-BE-003, SC-BE-024 — nudge/focus/reveal all sent, not just nudge
        // @spec SC-BE-030 — all three carry the 1-indexed conversion of the 0-indexed fixture text
        String requestJson = bedrockCoachClient.buildRequestJson("Help", HINT, List.of(), board());

        assertTrue(requestJson.contains("Only one digit can go in Row 2, Column 4."));
        assertTrue(requestJson.contains("Look at Row 2."));
        assertTrue(requestJson.contains("Place 4 in Row 2, Column 4."));
    }

    @Test
    void buildRequestJson_convertsZeroIndexedHintTextTo1Indexed() throws Exception {
        // @spec SC-BE-030 — reproduces the reported bug: a hint reveal of "Row 7, Column 3"
        // (0-indexed, i.e. the 8th row/4th column) must reach the LLM as "Row 8, Column 4" —
        // otherwise the coach repeats the 0-indexed numbers verbatim to the player, one off
        // from the cell actually highlighted on the board.
        HintResponse zeroIndexedHint = new HintResponse(
                "Naked Single", "naked-single", Difficulty.EASY, 1,
                "A cell in Row 7 has been reduced to exactly one possible candidate.",
                "Row 7, Column 3 has had every other digit eliminated.",
                "Row 7, Column 3 must be 1.", List.of(), List.of(), List.of(), List.of());

        String requestJson = bedrockCoachClient.buildRequestJson("Just tell me", zeroIndexedHint, List.of(), board());

        assertTrue(requestJson.contains("Row 8, Column 4 must be 1."));
        assertFalse(requestJson.contains("Row 7, Column 3"));
    }

    @Test
    void buildRequestJson_includesTurnNumberDerivedFromHistorySize() throws Exception {
        // @spec SC-BE-024
        List<ChatMessage> twoMessageHistory = List.of(
                new ChatMessage("user", "I'm stuck"),
                new ChatMessage("assistant", "{\"aiMessage\": \"Look at Row 1\", \"revealHint\": false}"));

        String requestJson = bedrockCoachClient.buildRequestJson("Tell me more", HINT, twoMessageHistory, board());

        assertTrue(requestJson.contains("TURN NUMBER: 2"));
    }

    @Test
    void buildRequestJson_suggestsNudgeLevelOnFirstTurn() throws Exception {
        // @spec SC-BE-024 — no history means this is the opening turn
        String requestJson = bedrockCoachClient.buildRequestJson("Help", HINT, List.of(), board());

        assertTrue(requestJson.contains("TURN NUMBER: 1 (suggested escalation level: NUDGE)"));
    }

    @Test
    void buildRequestJson_suggestsRevealLevelOnLaterTurn() throws Exception {
        // @spec SC-BE-024 — turn 3+ suggests REVEAL, but the LLM decides for itself
        List<ChatMessage> fourMessageHistory = List.of(
                new ChatMessage("user", "I'm stuck"),
                new ChatMessage("assistant", "{\"aiMessage\": \"Look at Row 1\", \"revealHint\": false}"),
                new ChatMessage("user", "Still not seeing it"),
                new ChatMessage("assistant", "{\"aiMessage\": \"Look closer\", \"revealHint\": false}"));

        String requestJson = bedrockCoachClient.buildRequestJson("Tell me more", HINT, fourMessageHistory, board());

        assertTrue(requestJson.contains("TURN NUMBER: 3 (suggested escalation level: REVEAL)"));
    }

    // ---- content logging (SC-BE-005..009, SC-BE-018, SC-BE-019) ----

    @Test
    void buildRequestLogLine_includesUserMessageBoardAndCandidates() throws Exception {
        // @spec SC-BE-005, SC-BE-006, SC-BE-007
        String cid = UUID.randomUUID().toString();
        String logLine = bedrockCoachClient.buildRequestLogLine("game-1", cid, 1000L, "I'm stuck", HINT, List.of(), board());

        JsonNode node = objectMapper.readTree(logLine);
        assertEquals("COACH_REQUEST", node.path("type").asText());
        assertEquals(cid, node.path("cid").asText());
        assertEquals("I'm stuck", node.path("userMessage").asText());

        // GRID row 0, col 0 is placed digit 5; col 2 is empty (0)
        assertEquals(5, node.path("board").get(0).get(0).asInt());
        assertEquals(0, node.path("board").get(0).get(2).asInt());

        // candidatesGrid wraps rows under "rows" (CandidatesGrid's existing wire-format serializer);
        // placed cells have no candidates, empty cells do
        assertTrue(node.path("candidatesGrid").path("rows").get(0).get(0).isEmpty());
        assertFalse(node.path("candidatesGrid").path("rows").get(0).get(2).isEmpty());
    }

    @Test
    void buildRequestLogLine_producesValidJsonWithQuotesAndNewlinesInUserMessage() throws Exception {
        // @spec SC-BE-018 — naive string templating would break on embedded quotes/newlines
        String tricky = "She said \"go\" and then\nhit enter";
        String logLine = bedrockCoachClient.buildRequestLogLine("game-1",
                UUID.randomUUID().toString(), 1000L, tricky, HINT, List.of(), board());

        JsonNode node = objectMapper.readTree(logLine);
        assertEquals(tricky, node.path("userMessage").asText());
    }

    @Test
    void buildResponseLogLine_includesAiMessageOnSuccessPath() throws Exception {
        // @spec SC-BE-008
        CoachAiClient.AiReply reply = new CoachAiClient.AiReply("Great question! Let's look at Row 1.", false, "nudge");
        String logLine = bedrockCoachClient.buildResponseLogLine("game-1",
                UUID.randomUUID().toString(), reply, 100, 50, 0, 0, 250L, false, null, null, null);

        JsonNode node = objectMapper.readTree(logLine);
        assertEquals("COACH_RESPONSE", node.path("type").asText());
        assertEquals("Great question! Let's look at Row 1.", node.path("aiMessage").asText());
        assertFalse(node.path("fallback").asBoolean());
    }

    @Test
    void buildResponseLogLine_includesResponseType_whenPresent() throws Exception {
        // @spec SC-BE-029
        CoachAiClient.AiReply reply = new CoachAiClient.AiReply("Great question!", false, "focus-hint");
        String logLine = bedrockCoachClient.buildResponseLogLine("game-1",
                UUID.randomUUID().toString(), reply, 100, 50, 0, 0, 250L, false, null, null, null);

        JsonNode node = objectMapper.readTree(logLine);
        assertEquals("focus-hint", node.path("responseType").asText());
    }

    @Test
    void buildResponseLogLine_omitsResponseType_onFallback() throws Exception {
        // @spec SC-BE-029 — fallback never calls Bedrock's structured output, so there is no
        // model-chosen category to log
        CoachAiClient.AiReply fallbackReply = new CoachAiClient.AiReply(HINT.nudge(), false, null);
        String logLine = bedrockCoachClient.buildResponseLogLine("game-1",
                UUID.randomUUID().toString(), fallbackReply, 0, 0, 0, 0, 6000L, true,
                "SdkClientException", "connection refused", null);

        JsonNode node = objectMapper.readTree(logLine);
        assertTrue(node.path("responseType").isMissingNode());
    }

    @Test
    void buildResponseLogLine_includesAiMessageOnFallbackPath() throws Exception {
        // @spec SC-BE-008 — fallback path logs the nudge text actually shown to the player
        CoachAiClient.AiReply fallbackReply = new CoachAiClient.AiReply(HINT.nudge(), false, null);
        String logLine = bedrockCoachClient.buildResponseLogLine("game-1",
                UUID.randomUUID().toString(), fallbackReply, 0, 0, 0, 0, 6000L, true,
                "SdkClientException", "connection refused", null);

        JsonNode node = objectMapper.readTree(logLine);
        assertEquals(HINT.nudge(), node.path("aiMessage").asText());
        assertTrue(node.path("fallback").asBoolean());
    }

    @Test
    void buildResponseLogLine_includesRawResponseText_whenPresent() throws Exception {
        // @spec SC-BE-023
        CoachAiClient.AiReply fallbackReply = new CoachAiClient.AiReply(HINT.nudge(), false, null);
        String logLine = bedrockCoachClient.buildResponseLogLine("game-1",
                UUID.randomUUID().toString(), fallbackReply, 0, 0, 0, 0, 250L, true,
                "ResponseParseFailure", "parse failure: JsonParseException: Unrecognized token 'I'",
                "I think you should look at Row 3.");

        JsonNode node = objectMapper.readTree(logLine);
        assertEquals("I think you should look at Row 3.", node.path("rawResponseText").asText());
    }

    @Test
    void buildResponseLogLine_omitsRawResponseText_whenNull() throws Exception {
        // @spec SC-BE-023 — no log bloat on a genuine successful reply
        CoachAiClient.AiReply reply = new CoachAiClient.AiReply("Great!", false, "nudge");
        String logLine = bedrockCoachClient.buildResponseLogLine("game-1",
                UUID.randomUUID().toString(), reply, 100, 50, 0, 0, 250L, false, null, null, null);

        JsonNode node = objectMapper.readTree(logLine);
        assertTrue(node.path("rawResponseText").isMissingNode());
    }

    @Test
    void requestAndResponseLogLines_carryPidFromGameId() throws Exception {
        // @spec SC-BE-020 — pid (gameId) on both lines so coach turns join with puzzle-play events
        String cid = UUID.randomUUID().toString();
        String requestLine = bedrockCoachClient.buildRequestLogLine("game-42", cid, 1000L, "Help", HINT, List.of(), board());
        String responseLine = bedrockCoachClient.buildResponseLogLine(
                "game-42", cid, new CoachAiClient.AiReply("ok", false, "nudge"), 1, 1, 0, 0, 10L, false, null, null, null);

        assertEquals("game-42", objectMapper.readTree(requestLine).path("pid").asText());
        assertEquals("game-42", objectMapper.readTree(responseLine).path("pid").asText());
    }

    @Test
    void requestAndResponseLogLines_shareSameCid() throws Exception {
        // @spec SC-BE-019
        String cid = UUID.randomUUID().toString();
        String requestLine = bedrockCoachClient.buildRequestLogLine("game-1", cid, 1000L, "Help", HINT, List.of(), board());
        String responseLine = bedrockCoachClient.buildResponseLogLine("game-1",
                cid, new CoachAiClient.AiReply("ok", false, "nudge"), 1, 1, 0, 0, 10L, false, null, null, null);

        assertEquals(objectMapper.readTree(requestLine).path("cid").asText(),
                     objectMapper.readTree(responseLine).path("cid").asText());
    }

    // ---- helpers ----

    private void stubBedrockResponse(String responseBody) {
        InvokeModelResponse mockResponse = invokeModelResponse(responseBody);
        when(bedrockRuntimeClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);
    }

    private static InvokeModelResponse invokeModelResponse(String responseBody) {
        InvokeModelResponse mockResponse = mock(InvokeModelResponse.class);
        when(mockResponse.body()).thenReturn(SdkBytes.fromUtf8String(responseBody.strip()));
        return mockResponse;
    }

    private static ConverseResponse converseResponse(String aiJsonText, int inputTokens, int outputTokens,
            int cacheReadTokens, int cacheWriteTokens) {
        return ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.fromText(aiJsonText))
                        .build()))
                .usage(TokenUsage.builder()
                        .inputTokens(inputTokens)
                        .outputTokens(outputTokens)
                        .cacheReadInputTokens(cacheReadTokens)
                        .cacheWriteInputTokens(cacheWriteTokens)
                        .build())
                .build();
    }
}
