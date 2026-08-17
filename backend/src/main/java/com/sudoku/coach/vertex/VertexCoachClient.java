package com.sudoku.coach.vertex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Type;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import com.sudoku.coach.CoachAiClient;
import com.sudoku.coach.bedrock.CoachPromptBuilder;
import com.sudoku.coach.web.ChatMessage;
import com.sudoku.domain.Board;
import com.sudoku.puzzle.web.HintResponse;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GCP-native coach LLM adapter — Gemini via Vertex AI. Authenticates with Application Default
 * Credentials (the Cloud Run runtime service account) via the shared quarkiverse google-cloud
 * credential producer, so it uses no long-lived keys and does not need the cross-cloud AWS Bedrock
 * secret. Builds the identical prompt as the Bedrock adapter (via {@link CoachPromptBuilder}) and
 * enforces the same JSON output schema through Gemini structured output; on any error or an
 * unparseable/blank reply it takes the same deterministic nudge fallback.
 *
 * @spec SC-GCP-001, SC-GCP-002, SC-GCP-003, SC-GCP-004, SC-GCP-005, SC-GCP-006, CP-GCP-090
 */
@ApplicationScoped
@Typed(VertexCoachClient.class)
@LookupIfProperty(name = "coach.ai.provider", stringValue = "vertex")
public class VertexCoachClient implements CoachAiClient {

    private static final org.jboss.logging.Logger LOG =
            org.jboss.logging.Logger.getLogger(VertexCoachClient.class);

    @Inject
    VertexAI vertexAI;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "coach.vertex.model-id")
    String modelId;

    // Gemini structured-output schema, mirroring CoachPromptBuilder.OUTPUT_SCHEMA_JSON (SC-BE-025):
    // the reply is constrained to {aiMessage:string, revealHint:boolean, responseType:enum} server-side.
    // @spec SC-GCP-003
    private static final Schema RESPONSE_SCHEMA = Schema.newBuilder()
            .setType(Type.OBJECT)
            .putProperties("aiMessage", Schema.newBuilder().setType(Type.STRING).build())
            .putProperties("revealHint", Schema.newBuilder().setType(Type.BOOLEAN).build())
            .putProperties("responseType", Schema.newBuilder().setType(Type.STRING)
                    .addAllEnum(List.of("nudge", "focus-hint", "reveal-answer", "gentle-redirect",
                            "off-topic-redirect", "celebrate-progress", "clarify-technique"))
                    .build())
            .addRequired("aiMessage").addRequired("revealHint").addRequired("responseType")
            .build();

    // @spec SC-GCP-001, SC-BE-009 — one Gemini call per request; SC-GCP-006 — fallback on error
    @Override
    public CallResult call(String pid, String userMessage, HintResponse hint, List<ChatMessage> history, Board board) {
        String cid = UUID.randomUUID().toString();
        LOG.infof("{\"type\":\"COACH_REQUEST\",\"provider\":\"vertex\",\"pid\":%s,\"cid\":\"%s\",\"modelId\":\"%s\",\"technique\":\"%s\",\"historyLen\":%d,\"userMsgLen\":%d}",
                pid == null ? "null" : "\"" + pid + "\"", cid, modelId, hint.techniqueName(), history.size(), userMessage.length());

        // @spec SC-GCP-004 — identical prompt as the Bedrock adapter via the shared builder
        String contextText = CoachPromptBuilder.buildContextBlock(userMessage, hint, history, board);
        try {
            GenerationConfig config = GenerationConfig.newBuilder()
                    .setResponseMimeType("application/json")
                    .setResponseSchema(RESPONSE_SCHEMA)
                    .setTemperature(0.7f)
                    .build();
            GenerativeModel model = new GenerativeModel(modelId, vertexAI)
                    .withSystemInstruction(ContentMaker.fromString(CoachPromptBuilder.SYSTEM_PROMPT))
                    .withGenerationConfig(config);

            GenerateContentResponse response = model.generateContent(buildContents(history, contextText));
            String text = ResponseHandler.getText(response);
            // @spec SC-GCP-005 — total tokens (prompt + candidates) fed to the monthly counter / rate limiter
            long tokens = response.getUsageMetadata().getTotalTokenCount();

            AiReply reply = parse(text);
            if (reply == null) {
                LOG.infof("{\"type\":\"COACH_RESPONSE\",\"provider\":\"vertex\",\"pid\":%s,\"cid\":\"%s\",\"fallback\":true,\"errorMsg\":\"unparseable or blank reply\",\"rawResponseText\":%s}",
                        pid == null ? "null" : "\"" + pid + "\"", cid, jsonString(text));
                return new CallResult(fallback(hint), 0L);
            }
            LOG.infof("{\"type\":\"COACH_RESPONSE\",\"provider\":\"vertex\",\"pid\":%s,\"cid\":\"%s\",\"revealHint\":%b,\"tokens\":%d,\"fallback\":false,\"responseType\":%s}",
                    pid == null ? "null" : "\"" + pid + "\"", cid, reply.revealHint(), tokens, jsonString(reply.responseType()));
            return new CallResult(reply, tokens);
        } catch (Exception e) {
            // @spec SC-GCP-006 — same deterministic fallback as the Bedrock adapter on any SDK/network error
            LOG.infof("{\"type\":\"COACH_RESPONSE\",\"provider\":\"vertex\",\"pid\":%s,\"cid\":\"%s\",\"fallback\":true,\"errorType\":\"%s\",\"errorMsg\":%s}",
                    pid == null ? "null" : "\"" + pid + "\"", cid, e.getClass().getSimpleName(), jsonString(e.getMessage()));
            return new CallResult(fallback(hint), 0L);
        }
    }

    // Prior turns as alternating user/model content, then the final user turn (the context block).
    private static List<Content> buildContents(List<ChatMessage> history, String finalUserText) {
        List<Content> contents = new ArrayList<>();
        for (ChatMessage m : history) {
            String role = "assistant".equals(m.role()) ? "model" : "user";
            contents.add(ContentMaker.forRole(role).fromString(m.content()));
        }
        contents.add(ContentMaker.forRole("user").fromString(finalUserText));
        return contents;
    }

    // Extract the first top-level {...} and map to AiReply; null on parse failure or blank aiMessage
    // (@spec SC-GCP-006, mirroring SC-BE-022/023). The schema (SC-GCP-003) makes drift near-impossible,
    // but the parse is defensive regardless.
    AiReply parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
            String message = node.path("aiMessage").asText("");
            if (message.isBlank()) {
                return null;
            }
            String responseType = node.hasNonNull("responseType") ? node.get("responseType").asText() : null;
            return new AiReply(message, node.path("revealHint").asBoolean(false), responseType);
        } catch (Exception e) {
            return null;
        }
    }

    // @spec SC-GCP-006 — same deterministic nudge fallback as BedrockCoachClient.fallback
    private static AiReply fallback(HintResponse hint) {
        return new AiReply(hint.nudge(), false, null);
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"";
    }
}
