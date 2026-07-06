package com.sudoku.puzzle;

import com.sudoku.domain.Grid;
import com.sudoku.dto.ChatMessage;
import com.sudoku.dto.HintResponse;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// @spec SC-BE-009, SC-BE-012, SC-BE-013, SC-BE-015, SC-BE-016
@QuarkusTest
class BedrockCoachClientTest {

    @InjectMock
    BedrockRuntimeClient bedrockRuntimeClient;

    @Inject
    BedrockCoachClient bedrockCoachClient;

    private static final Grid BOARD = Grid.of(List.of(
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

    private static final HintResponse HINT = new HintResponse(
            "Naked Single", "naked-single", Difficulty.EASY, 1,
            "Only one digit can go in Row 1, Column 3.", "Look at Row 1.",
            "Place 4 in Row 1, Column 3.", List.of(), List.of(), List.of(), List.of());

    @Test
    void call_validBedrockResponse_returnsParsedAiReply() {
        stubBedrockResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"Great question! Let's look at Row 1 together.\\",\\"revealHint\\":false}"}]}
                """);

        BedrockCoachClient.AiReply reply = bedrockCoachClient.call("I'm stuck", HINT, List.of(), BOARD).reply();

        assertEquals("Great question! Let's look at Row 1 together.", reply.aiMessage());
        assertFalse(reply.revealHint());
    }

    @Test
    void call_revealHintTrue_propagatesFlag() {
        stubBedrockResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"Place 4 in Row 1, Column 3.\\",\\"revealHint\\":true}"}]}
                """);

        BedrockCoachClient.AiReply reply = bedrockCoachClient.call("Just tell me", HINT, List.of(), BOARD).reply();

        assertTrue(reply.revealHint());
    }

    @Test
    void call_invalidJsonInResponseText_fallsBackToNudge() {
        // @spec SC-BE-013
        stubBedrockResponse("""
                {"content":[{"type":"text","text":"This is not JSON at all"}]}
                """);

        BedrockCoachClient.AiReply reply = bedrockCoachClient.call("Help", HINT, List.of(), BOARD).reply();

        assertEquals(HINT.nudge(), reply.aiMessage());
        assertFalse(reply.revealHint());
    }

    @Test
    void call_blankAiMessageInResponse_fallsBackToNudge() {
        // @spec SC-BE-013
        stubBedrockResponse("""
                {"content":[{"type":"text","text":"{\\"aiMessage\\":\\"\\",\\"revealHint\\":false}"}]}
                """);

        BedrockCoachClient.AiReply reply = bedrockCoachClient.call("Help", HINT, List.of(), BOARD).reply();

        assertEquals(HINT.nudge(), reply.aiMessage());
    }

    @Test
    void call_sdkException_fallsBackToNudge() {
        // @spec SC-BE-012 — fallback on Bedrock timeout or SDK error
        when(bedrockRuntimeClient.invokeModel(any(InvokeModelRequest.class)))
                .thenThrow(SdkClientException.builder().message("connection refused").build());

        BedrockCoachClient.AiReply reply = bedrockCoachClient.call("Help", HINT, List.of(), BOARD).reply();

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

        String requestJson = bedrockCoachClient.buildRequestJson("Tell me more", HINT, history, BOARD);

        assertTrue(requestJson.contains("I see digits 1 and 2 in Row 1"));
        assertTrue(requestJson.contains("Good start!"));
    }

    @Test
    void buildRequestJson_includesCacheControlOnSystemPrompt() throws Exception {
        String requestJson = bedrockCoachClient.buildRequestJson("Help", HINT, List.of(), BOARD);

        assertTrue(requestJson.contains("cache_control"));
        assertTrue(requestJson.contains("ephemeral"));
    }

    @Test
    void buildRequestJson_includesBoardAndTechnique() throws Exception {
        String requestJson = bedrockCoachClient.buildRequestJson("Help", HINT, List.of(), BOARD);

        assertTrue(requestJson.contains("CURRENT BOARD STATE"));
        assertTrue(requestJson.contains(HINT.techniqueName()));
        assertTrue(requestJson.contains(HINT.nudge()));
    }

    // ---- helpers ----

    private void stubBedrockResponse(String responseBody) {
        InvokeModelResponse mockResponse = mock(InvokeModelResponse.class);
        when(mockResponse.body()).thenReturn(SdkBytes.fromUtf8String(responseBody.strip()));
        when(bedrockRuntimeClient.invokeModel(any(InvokeModelRequest.class))).thenReturn(mockResponse);
    }
}
