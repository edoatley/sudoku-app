package com.sudoku.coach.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudoku.domain.Board;
import com.sudoku.domain.Grid;
import com.sudoku.coach.web.ChatMessage;
import com.sudoku.puzzle.web.HintResponse;
import com.sudoku.puzzle.hint.Difficulty;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// @spec SC-BE-005, SC-BE-006, SC-BE-007, SC-BE-008, SC-BE-009, SC-BE-012, SC-BE-013, SC-BE-015, SC-BE-016, SC-BE-018, SC-BE-019, SC-BE-021, SC-BE-022
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

    @Test
    void call_validBedrockResponse_returnsParsedAiReply() {
        stubBedrockResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"Great question! Let's look at Row 1 together.\\",\\"revealHint\\":false}"}]}
                """);

        BedrockCoachClient.AiReply reply = bedrockCoachClient.call("game-1", "I'm stuck", HINT, List.of(), board()).reply();

        assertEquals("Great question! Let's look at Row 1 together.", reply.aiMessage());
        assertFalse(reply.revealHint());
    }

    @Test
    void call_revealHintTrue_propagatesFlag() {
        stubBedrockResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"Place 4 in Row 1, Column 3.\\",\\"revealHint\\":true}"}]}
                """);

        BedrockCoachClient.AiReply reply = bedrockCoachClient.call("game-1", "Just tell me", HINT, List.of(), board()).reply();

        assertTrue(reply.revealHint());
    }

    @Test
    void call_invalidJsonInResponseText_fallsBackToNudge() {
        // @spec SC-BE-013
        stubBedrockResponse("""
                {"content":[{"type":"text","text":"This is not JSON at all"}]}
                """);

        BedrockCoachClient.AiReply reply = bedrockCoachClient.call("game-1", "Help", HINT, List.of(), board()).reply();

        assertEquals(HINT.nudge(), reply.aiMessage());
        assertFalse(reply.revealHint());
    }

    @Test
    void call_blankAiMessageInResponse_fallsBackToNudge() {
        // @spec SC-BE-013
        stubBedrockResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"\\",\\"revealHint\\":false}"}]}
                """);

        BedrockCoachClient.AiReply reply = bedrockCoachClient.call("game-1", "Help", HINT, List.of(), board()).reply();

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
    void parseResponse_setsFallbackReason_whenAiMessageBlank() {
        // @spec SC-BE-021
        InvokeModelResponse response = invokeModelResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"\\",\\"revealHint\\":false}"}]}
                """);

        BedrockCoachClient.ParsedResponse parsed = bedrockCoachClient.parseResponse(response, HINT);

        assertNotNull(parsed.fallbackReason());
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
    void call_sdkException_fallsBackToNudge() {
        // @spec SC-BE-012 — fallback on Bedrock timeout or SDK error
        when(bedrockRuntimeClient.invokeModel(any(InvokeModelRequest.class)))
                .thenThrow(SdkClientException.builder().message("connection refused").build());

        BedrockCoachClient.AiReply reply = bedrockCoachClient.call("game-1", "Help", HINT, List.of(), board()).reply();

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
    void buildRequestJson_includesBoardAndTechnique() throws Exception {
        String requestJson = bedrockCoachClient.buildRequestJson("Help", HINT, List.of(), board());

        assertTrue(requestJson.contains("CURRENT BOARD STATE"));
        assertTrue(requestJson.contains(HINT.techniqueName()));
        assertTrue(requestJson.contains(HINT.nudge()));
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
        BedrockCoachClient.AiReply reply = new BedrockCoachClient.AiReply("Great question! Let's look at Row 1.", false);
        String logLine = bedrockCoachClient.buildResponseLogLine("game-1",
                UUID.randomUUID().toString(), reply, 100, 50, 0, 0, 250L, false, null, null);

        JsonNode node = objectMapper.readTree(logLine);
        assertEquals("COACH_RESPONSE", node.path("type").asText());
        assertEquals("Great question! Let's look at Row 1.", node.path("aiMessage").asText());
        assertFalse(node.path("fallback").asBoolean());
    }

    @Test
    void buildResponseLogLine_includesAiMessageOnFallbackPath() throws Exception {
        // @spec SC-BE-008 — fallback path logs the nudge text actually shown to the player
        BedrockCoachClient.AiReply fallbackReply = new BedrockCoachClient.AiReply(HINT.nudge(), false);
        String logLine = bedrockCoachClient.buildResponseLogLine("game-1",
                UUID.randomUUID().toString(), fallbackReply, 0, 0, 0, 0, 6000L, true,
                "SdkClientException", "connection refused");

        JsonNode node = objectMapper.readTree(logLine);
        assertEquals(HINT.nudge(), node.path("aiMessage").asText());
        assertTrue(node.path("fallback").asBoolean());
    }

    @Test
    void requestAndResponseLogLines_carryPidFromGameId() throws Exception {
        // @spec SC-BE-020 — pid (gameId) on both lines so coach turns join with puzzle-play events
        String cid = UUID.randomUUID().toString();
        String requestLine = bedrockCoachClient.buildRequestLogLine("game-42", cid, 1000L, "Help", HINT, List.of(), board());
        String responseLine = bedrockCoachClient.buildResponseLogLine(
                "game-42", cid, new BedrockCoachClient.AiReply("ok", false), 1, 1, 0, 0, 10L, false, null, null);

        assertEquals("game-42", objectMapper.readTree(requestLine).path("pid").asText());
        assertEquals("game-42", objectMapper.readTree(responseLine).path("pid").asText());
    }

    @Test
    void requestAndResponseLogLines_shareSameCid() throws Exception {
        // @spec SC-BE-019
        String cid = UUID.randomUUID().toString();
        String requestLine = bedrockCoachClient.buildRequestLogLine("game-1", cid, 1000L, "Help", HINT, List.of(), board());
        String responseLine = bedrockCoachClient.buildResponseLogLine("game-1",
                cid, new BedrockCoachClient.AiReply("ok", false), 1, 1, 0, 0, 10L, false, null, null);

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
}
