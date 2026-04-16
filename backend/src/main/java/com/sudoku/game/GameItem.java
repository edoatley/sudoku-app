package com.sudoku.game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudoku.dto.GameState;
import com.sudoku.dto.GameUpdateRequest;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.List;

/**
 * DynamoDB entity for a Sudoku game. Grids are stored as JSON strings in DynamoDB
 * and converted automatically by the registered converters.
 *
 * Kept separate from GameState (the REST DTO) so the two concerns stay independent.
 */
@DynamoDbBean
public class GameItem {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String userId;
    private String gameId;
    private String difficulty;
    private String originalGrid;
    private String solutionGrid;
    private String currentGrid;
    private String candidates;
    private int timeSpentSeconds;
    private String status;
    private int hintsUsed;
    private String startedAt;
    private String endedAt;

    // --- composite key ---

    @DynamoDbPartitionKey
    @DynamoDbAttribute("userId")
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @DynamoDbSortKey
    @DynamoDbAttribute("gameId")
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    // --- plain string attributes ---

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getOriginalGrid() { return originalGrid; }
    public void setOriginalGrid(String originalGrid) { this.originalGrid = originalGrid; }

    public String getSolutionGrid() { return solutionGrid; }
    public void setSolutionGrid(String solutionGrid) { this.solutionGrid = solutionGrid; }

    public String getCurrentGrid() { return currentGrid; }
    public void setCurrentGrid(String currentGrid) { this.currentGrid = currentGrid; }

    public String getCandidates() { return candidates; }
    public void setCandidates(String candidates) { this.candidates = candidates; }

    public int getTimeSpentSeconds() { return timeSpentSeconds; }
    public void setTimeSpentSeconds(int timeSpentSeconds) { this.timeSpentSeconds = timeSpentSeconds; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getHintsUsed() { return hintsUsed; }
    public void setHintsUsed(int hintsUsed) { this.hintsUsed = hintsUsed; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

    public String getEndedAt() { return endedAt; }
    public void setEndedAt(String endedAt) { this.endedAt = endedAt; }

    // --- factory methods ---

    static GameItem from(GameState state) {
        GameItem item = new GameItem();
        item.setUserId(state.userId());
        item.setGameId(state.gameId());
        item.setDifficulty(state.difficulty());
        item.setOriginalGrid(toJson(state.originalGrid()));
        item.setSolutionGrid(state.solutionGrid() != null ? toJson(state.solutionGrid()) : null);
        item.setCurrentGrid(toJson(state.currentGrid()));
        item.setCandidates(toJson(state.candidates()));
        item.setTimeSpentSeconds(state.timeSpentSeconds());
        item.setStatus(state.status());
        item.setHintsUsed(state.hintsUsed());
        item.setStartedAt(state.startedAt());
        item.setEndedAt(state.endedAt());
        return item;
    }

    public GameState toGameState() {
        return new GameState(
                userId,
                gameId,
                difficulty,
                fromJson(originalGrid, new TypeReference<List<List<Integer>>>() {}),
                solutionGrid != null ? fromJson(solutionGrid, new TypeReference<List<List<Integer>>>() {}) : null,
                fromJson(currentGrid, new TypeReference<List<List<Integer>>>() {}),
                fromJson(candidates, new TypeReference<List<List<List<Integer>>>>() {}),
                timeSpentSeconds,
                status,
                hintsUsed,
                startedAt,
                endedAt
        );
    }

    /**
     * Marks this game as abandoned by the server when the player starts a new game.
     * Only status and endedAt are mutated — grid data is preserved so the record
     * remains queryable for audit or statistics purposes.
     *
     * @param now ISO-8601 UTC timestamp string for the abandonment moment
     */
    void markAbandoned(String now) {
        setStatus(GameStatus.ABANDONED.getValue());
        setEndedAt(now);
    }

    void applyUpdate(GameUpdateRequest request, String now) {
        setCurrentGrid(toJson(request.currentGrid()));
        setCandidates(toJson(request.candidates()));
        setTimeSpentSeconds(request.timeSpentSeconds());
        if (Boolean.TRUE.equals(request.isComplete())) {
            setStatus(GameStatus.SOLVED.getValue());
            setEndedAt(now);
        } else {
            setStatus(GameStatus.IN_PROGRESS.getValue());
        }
        if (request.hintsUsed() != null) setHintsUsed(request.hintsUsed());
    }

    // --- JSON helpers (package-private for use by this class only) ---

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise grid to JSON", e);
        }
    }

    private static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise grid from JSON", e);
        }
    }
}
