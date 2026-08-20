package com.sudoku.coach.vertex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.CachedContent;
import com.google.genai.types.CachedContentUsageMetadata;
import com.google.genai.types.Content;
import com.google.genai.types.CreateCachedContentConfig;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GCP-native coach LLM adapter — Gemini via Vertex AI. Authenticates with Application Default
 * Credentials (the Cloud Run runtime service account) via {@link GenAiClientProducer}, so it uses
 * no long-lived keys and does not need the cross-cloud AWS Bedrock secret. Builds the identical
 * prompt as the Bedrock adapter (via {@link CoachPromptBuilder}) and
 * enforces the same JSON output schema through Gemini structured output; on any error or an
 * unparseable/blank reply it takes the same deterministic nudge fallback. Caches the tutor system
 * prompt via a Vertex AI {@code CachedContent} resource, reused across calls until near expiry —
 * unlike Bedrock's automatic per-call caching, this is an explicit resource this class creates
 * and holds itself; any failure to create or reuse it degrades to an uncached call rather than
 * breaking coaching.
 *
 * @spec SC-GCP-001, SC-GCP-002, SC-GCP-003, SC-GCP-004, SC-GCP-005, SC-GCP-006, SC-GCP-008,
 *       SC-GCP-009, CP-GCP-090
 */
@ApplicationScoped
@Typed(VertexCoachClient.class)
@LookupIfProperty(name = "coach.ai.provider", stringValue = "vertex")
public class VertexCoachClient implements CoachAiClient {

    private static final org.jboss.logging.Logger LOG =
            org.jboss.logging.Logger.getLogger(VertexCoachClient.class);

    @Inject
    Client client;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "coach.vertex.model-id")
    String modelId;

    // Gemini structured-output schema, mirroring CoachPromptBuilder.OUTPUT_SCHEMA_JSON (SC-BE-025):
    // the reply is constrained to {aiMessage:string, revealHint:boolean, responseType:enum} server-side.
    // @spec SC-GCP-003
    private static final Schema RESPONSE_SCHEMA = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(Map.of(
                    "aiMessage", Schema.builder().type(Type.Known.STRING).build(),
                    "revealHint", Schema.builder().type(Type.Known.BOOLEAN).build(),
                    "responseType", Schema.builder().type(Type.Known.STRING)
                            .enum_(List.of("nudge", "focus-hint", "reveal-answer", "gentle-redirect",
                                    "off-topic-redirect", "celebrate-progress", "clarify-technique"))
                            .build()))
            .required("aiMessage", "revealHint", "responseType")
            .build();

    // @spec SC-GCP-008 — TTL for the cached tutor system prompt; refresh a little early so a
    // call never races the cache's actual server-side expiry.
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final Duration CACHE_REFRESH_MARGIN = Duration.ofMinutes(5);

    private volatile CachedPromptHandle cachedPrompt;

    private record CachedPromptHandle(String name, Instant expiresAt) {}

    // name == null means no usable cache; writeTokens is nonzero only on the call that just
    // (re)created the cache.
    private record CacheLookup(String name, long writeTokens) {}

    // @spec SC-GCP-001, SC-BE-009 — one Gemini call per request; SC-GCP-006 — fallback on error
    @Override
    public CallResult call(String pid, String userMessage, HintResponse hint, List<ChatMessage> history, Board board) {
        String cid = UUID.randomUUID().toString();
        LOG.infof("{\"type\":\"COACH_REQUEST\",\"provider\":\"vertex\",\"pid\":%s,\"cid\":\"%s\",\"modelId\":\"%s\",\"technique\":\"%s\",\"historyLen\":%d,\"userMsgLen\":%d}",
                pid == null ? "null" : "\"" + pid + "\"", cid, modelId, hint.techniqueName(), history.size(), userMessage.length());

        // @spec SC-GCP-004 — identical prompt as the Bedrock adapter via the shared builder
        String contextText = CoachPromptBuilder.buildContextBlock(userMessage, hint, history, board);
        try {
            // @spec SC-GCP-008 — cache the tutor system prompt; getOrCreateCachedPrompt() never
            // throws, so a cache failure degrades to an uncached call rather than reaching the
            // outer catch's nudge fallback.
            CacheLookup cache = getOrCreateCachedPrompt();
            GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .responseSchema(RESPONSE_SCHEMA)
                    .temperature(0.7f);
            if (cache.name() != null) {
                configBuilder.cachedContent(cache.name());
            } else {
                configBuilder.systemInstruction(Content.fromParts(Part.fromText(CoachPromptBuilder.SYSTEM_PROMPT)));
            }
            GenerateContentConfig config = configBuilder.build();

            GenerateContentResponse response = client.models.generateContent(modelId, buildContents(history, contextText), config);
            String text = response.text();
            // @spec SC-GCP-005 — total tokens (prompt + candidates) fed to the monthly counter / rate limiter
            long tokens = response.usageMetadata()
                    .flatMap(GenerateContentResponseUsageMetadata::totalTokenCount)
                    .map(Integer::longValue)
                    .orElse(0L);
            // @spec SC-GCP-009 — cached-content tokens, 0 when this call ran uncached
            long cacheReadTokens = response.usageMetadata()
                    .flatMap(GenerateContentResponseUsageMetadata::cachedContentTokenCount)
                    .map(Integer::longValue)
                    .orElse(0L);

            AiReply reply = parse(text);
            if (reply == null) {
                LOG.infof("{\"type\":\"COACH_RESPONSE\",\"provider\":\"vertex\",\"pid\":%s,\"cid\":\"%s\",\"fallback\":true,\"errorMsg\":\"unparseable or blank reply\",\"rawResponseText\":%s}",
                        pid == null ? "null" : "\"" + pid + "\"", cid, jsonString(text));
                return new CallResult(fallback(hint), 0L);
            }
            LOG.infof("{\"type\":\"COACH_RESPONSE\",\"provider\":\"vertex\",\"pid\":%s,\"cid\":\"%s\",\"revealHint\":%b,\"tokens\":%d,\"cacheReadTokens\":%d,\"cacheWriteTokens\":%d,\"fallback\":false,\"responseType\":%s}",
                    pid == null ? "null" : "\"" + pid + "\"", cid, reply.revealHint(), tokens, cacheReadTokens, cache.writeTokens(), jsonString(reply.responseType()));
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
            contents.add(Content.builder().role(role).parts(Part.fromText(m.content())).build());
        }
        contents.add(Content.builder().role("user").parts(Part.fromText(finalUserText)).build());
        return contents;
    }

    // @spec SC-GCP-008 — reuse the current cache handle if still fresh; otherwise (re)create it.
    // synchronized is fine at this call volume (SC-RL-005 rate-limits to 5 calls/min/user) — no
    // need for finer-grained locking. Never throws: any failure here must degrade to an uncached
    // call, not break coaching.
    private synchronized CacheLookup getOrCreateCachedPrompt() {
        CachedPromptHandle current = cachedPrompt;
        Instant now = Instant.now();
        if (current != null && isFresh(current.expiresAt(), now, CACHE_REFRESH_MARGIN)) {
            return new CacheLookup(current.name(), 0L);
        }
        try {
            CachedContent created = client.caches.create(modelId, CreateCachedContentConfig.builder()
                    .displayName("sudoku-coach-tutor-prompt")
                    .systemInstruction(Content.fromParts(Part.fromText(CoachPromptBuilder.SYSTEM_PROMPT)))
                    .ttl(CACHE_TTL)
                    .build());
            String name = created.name().orElse(null);
            if (name == null) {
                return new CacheLookup(null, 0L);
            }
            cachedPrompt = new CachedPromptHandle(name, now.plus(CACHE_TTL));
            long writeTokens = created.usageMetadata()
                    .flatMap(CachedContentUsageMetadata::totalTokenCount)
                    .map(Integer::longValue)
                    .orElse(0L);
            return new CacheLookup(name, writeTokens);
        } catch (Exception e) {
            LOG.warnf("Vertex context cache creation failed, proceeding uncached: %s", e.getMessage());
            return new CacheLookup(null, 0L);
        }
    }

    // @spec SC-GCP-008
    static boolean isFresh(Instant expiresAt, Instant now, Duration margin) {
        return now.isBefore(expiresAt.minus(margin));
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
