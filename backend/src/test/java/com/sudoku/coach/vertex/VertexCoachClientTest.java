package com.sudoku.coach.vertex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudoku.coach.CoachAiClient.AiReply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Vertex adapter's structured-reply parsing + fallback trigger (SC-GCP-003/006)
 * and the cached-prompt freshness predicate (SC-GCP-008). The live Gemini call — including actual
 * cache creation/reuse — is exercised by the coach-quality harness, not here.
 */
class VertexCoachClientTest {

    private VertexCoachClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = new VertexCoachClient();
        Field f = VertexCoachClient.class.getDeclaredField("objectMapper");
        f.setAccessible(true);
        f.set(client, new ObjectMapper());
    }

    @Test
    void parse_validJson_mapsAllFields() {
        AiReply r = client.parse("{\"aiMessage\":\"Look at Row 1.\",\"revealHint\":false,\"responseType\":\"nudge\"}");
        assertNotNull(r);
        assertEquals("Look at Row 1.", r.aiMessage());
        assertFalse(r.revealHint());
        assertEquals("nudge", r.responseType());
    }

    @Test
    void parse_jsonWrappedInProse_extractsFirstObject() {
        AiReply r = client.parse("Sure!\n```json\n{\"aiMessage\":\"Place 4.\",\"revealHint\":true,\"responseType\":\"reveal-answer\"}\n```");
        assertNotNull(r);
        assertTrue(r.revealHint());
        assertEquals("Place 4.", r.aiMessage());
    }

    @Test
    void parse_blankAiMessage_returnsNull_forFallback() {
        assertNull(client.parse("{\"aiMessage\":\"\",\"revealHint\":false}"));
    }

    @Test
    void parse_nonJson_returnsNull_forFallback() {
        assertNull(client.parse("This is not JSON at all"));
    }

    @Test
    void parse_nullOrBlank_returnsNull() {
        assertNull(client.parse(null));
        assertNull(client.parse("   "));
    }

    @Test
    void parse_missingResponseType_yieldsNullResponseType() {
        AiReply r = client.parse("{\"aiMessage\":\"Hi\",\"revealHint\":false}");
        assertNotNull(r);
        assertNull(r.responseType());
    }

    @Test
    void isFresh_wellBeforeExpiryMinusMargin_isTrue() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Instant expiresAt = now.plus(Duration.ofHours(1));
        assertTrue(VertexCoachClient.isFresh(expiresAt, now, Duration.ofMinutes(5)));
    }

    @Test
    void isFresh_withinMarginOfExpiry_isFalse() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Instant expiresAt = now.plus(Duration.ofMinutes(3));
        assertFalse(VertexCoachClient.isFresh(expiresAt, now, Duration.ofMinutes(5)));
    }

    @Test
    void isFresh_alreadyExpired_isFalse() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Instant expiresAt = now.minus(Duration.ofMinutes(1));
        assertFalse(VertexCoachClient.isFresh(expiresAt, now, Duration.ofMinutes(5)));
    }
}
