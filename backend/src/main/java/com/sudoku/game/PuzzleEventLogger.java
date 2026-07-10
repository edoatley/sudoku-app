package com.sudoku.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sudoku.domain.Grid;
import com.sudoku.game.web.PuzzleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits one structured JSON log line per puzzle-play action the client reports on a save,
 * for observability of what the player does around the AI coach (see
 * {@code docs/llds/game-lifecycle.md} Puzzle-Play Event Logging).
 *
 * <p>Lines are built with the injected Jackson {@link ObjectMapper} (never string templating)
 * and share {@code pid} (the gameId) so a whole play session — including the {@code COACH_*}
 * lines — can be joined. {@code NUMBER_RESULT} is derived here from the authoritative solution;
 * every other type is a pass-through of the client action. Client-supplied coordinates and
 * digits are untrusted: anything malformed is logged at WARN and skipped, never thrown.
 */
@ApplicationScoped
public class PuzzleEventLogger {

    private static final Logger LOG = Logger.getLogger(PuzzleEventLogger.class);

    /** Defensive cap so a runaway client cannot flood the log on a single request. */
    static final int MAX_EVENTS = 500;

    private final ObjectMapper objectMapper;

    @Inject
    public PuzzleEventLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Builds and emits a log line for each processed event.
     *
     * @param pid      the gameId, correlation key across all lines for this play session
     * @param userId   the JWT principal (trustworthy — not client-asserted)
     * @param solution the game's solution grid, used to derive {@code NUMBER_RESULT}
     * @param events   the buffered client actions (may be null)
     */
    // @spec GL-BE-040, GL-BE-041, GL-BE-042, GL-BE-043, GL-BE-044, GL-BE-045
    public void log(String pid, String userId, Grid solution, List<PuzzleEvent> events) {
        for (String line : buildLines(pid, userId, solution, events)) {
            LOG.info(line);
        }
    }

    /**
     * Pure builder: turns the buffered events into the JSON lines that would be logged,
     * skipping anything malformed. Separated from {@link #log} so the mapping is unit-testable.
     */
    List<String> buildLines(String pid, String userId, Grid solution, List<PuzzleEvent> events) {
        List<String> lines = new ArrayList<>();
        if (events == null) {
            return lines;
        }
        long ts = System.currentTimeMillis();
        int processed = 0;
        for (PuzzleEvent event : events) {
            if (processed >= MAX_EVENTS) {
                break;
            }
            processed++;
            appendEvent(lines, pid, userId, ts, solution, event);
        }
        return lines;
    }

    private void appendEvent(List<String> lines, String pid, String userId, long ts, Grid solution,
                             PuzzleEvent event) {
        String type = event.type();
        if (type == null) {
            warnSkip(event);
            return;
        }
        switch (type) {
            case "NUMBER" -> {
                if (!validCell(event.r(), event.c()) || !validDigit(event.v())) {
                    warnSkip(event);
                    return;
                }
                ObjectNode number = base("NUMBER", pid, userId, ts, event.clientTs());
                number.put("r", event.r());
                number.put("c", event.c());
                number.put("v", event.v());
                emit(lines, number);

                ObjectNode result = base("NUMBER_RESULT", pid, userId, ts, event.clientTs());
                result.put("r", event.r());
                result.put("c", event.c());
                result.put("v", event.v());
                result.put("correct", solution != null && solution.cell(event.r(), event.c()) == event.v());
                emit(lines, result);
            }
            case "NUMBER_CLEAR" -> {
                if (!validCell(event.r(), event.c())) {
                    warnSkip(event);
                    return;
                }
                ObjectNode node = base("NUMBER_CLEAR", pid, userId, ts, event.clientTs());
                node.put("r", event.r());
                node.put("c", event.c());
                emit(lines, node);
            }
            case "HINT_REQUEST" -> {
                ObjectNode node = base("HINT_REQUEST", pid, userId, ts, event.clientTs());
                node.put("cid", event.cid());
                if (event.minRank() != null) {
                    node.put("minRank", event.minRank());
                }
                if (event.excludedRanks() != null) {
                    node.set("excludedRanks", objectMapper.valueToTree(event.excludedRanks()));
                }
                emit(lines, node);
            }
            case "HINT_RESPONSE" -> {
                ObjectNode node = base("HINT_RESPONSE", pid, userId, ts, event.clientTs());
                node.put("cid", event.cid());
                node.put("techniqueName", event.techniqueName());
                if (event.strategyRank() != null) {
                    node.put("strategyRank", event.strategyRank());
                }
                node.put("difficulty", event.difficulty());
                if (event.found() != null) {
                    node.put("found", event.found());
                }
                emit(lines, node);
            }
            case "EVENTS_TRUNCATED" -> emit(lines, base("EVENTS_TRUNCATED", pid, userId, ts, event.clientTs()));
            default -> warnSkip(event);
        }
    }

    private ObjectNode base(String type, String pid, String userId, long ts, Long clientTs) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        node.put("pid", pid);
        node.put("userId", userId);
        node.put("ts", ts);
        if (clientTs != null) {
            node.put("clientTs", clientTs);
        }
        return node;
    }

    private void emit(List<String> lines, ObjectNode node) {
        try {
            lines.add(objectMapper.writeValueAsString(node));
        } catch (Exception e) {
            LOG.warnf("Failed to serialize puzzle-play event line: %s", e.getMessage());
        }
    }

    private static boolean validCell(Integer r, Integer c) {
        return r != null && c != null && r >= 0 && r < 9 && c >= 0 && c < 9;
    }

    private static boolean validDigit(Integer v) {
        return v != null && v >= 1 && v <= 9;
    }

    private static void warnSkip(PuzzleEvent event) {
        LOG.warnf("Skipping malformed puzzle-play event: type=%s r=%s c=%s v=%s",
                event.type(), event.r(), event.c(), event.v());
    }
}
