package com.sudoku.coach;

import com.sudoku.coach.web.ChatMessage;
import com.sudoku.domain.Board;
import com.sudoku.puzzle.web.HintResponse;

import java.util.List;

/**
 * Output port for the coach's LLM call. Placing the provider behind this port lets each cloud use
 * its native model: {@code BedrockCoachClient} (Claude via Amazon Bedrock — AWS, and the interim
 * cross-cloud path on GCP) and {@code VertexCoachClient} (Gemini via Vertex AI, authenticated by the
 * Cloud Run runtime service account — GCP-native). The active adapter is selected at runtime by
 * {@code CoachAiClientProducer} on the {@code coach.ai.provider} property; the pedagogical contract
 * (prompt, structured-output schema, fallback, token accounting) is identical across providers.
 *
 * @spec SC-GCP-001, SC-GCP-004
 */
public interface CoachAiClient {

    /**
     * Produce a coaching reply for the player's message, given the deterministic hint and context.
     *
     * @spec SC-BE-009 — one AI call per request; SC-BE-015, SC-BE-016 — fallback on error
     */
    CallResult call(String pid, String userMessage, HintResponse hint, List<ChatMessage> history, Board board);

    /**
     * The structured reply. {@code responseType} is null on the fallback path and, on a genuine
     * parse, one of the enum values of the shared output schema (logging/testing only — never
     * surfaced to the player; see {@code SC-BE-028}).
     */
    record AiReply(String aiMessage, boolean revealHint, String responseType) {}

    record CallResult(AiReply reply, long tokensUsed) {}
}
