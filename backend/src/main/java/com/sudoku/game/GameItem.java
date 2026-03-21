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
    private String currentGrid;
    private String candidates;
    private int timeSpentSeconds;
    private String status;

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

    public String getCurrentGrid() { return currentGrid; }
    public void setCurrentGrid(String currentGrid) { this.currentGrid = currentGrid; }

    public String getCandidates() { return candidates; }
    public void setCandidates(String candidates) { this.candidates = candidates; }

    public int getTimeSpentSeconds() { return timeSpentSeconds; }
    public void setTimeSpentSeconds(int timeSpentSeconds) { this.timeSpentSeconds = timeSpentSeconds; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // --- factory methods ---

    static GameItem from(GameState state) {
        GameItem item = new GameItem();
        item.setUserId(state.userId());
        item.setGameId(state.gameId());
        item.setDifficulty(state.difficulty());
        item.setOriginalGrid(toJson(state.originalGrid()));
        item.setCurrentGrid(toJson(state.currentGrid()));
        item.setCandidates(toJson(state.candidates()));
        item.setTimeSpentSeconds(state.timeSpentSeconds());
        item.setStatus(state.status());
        return item;
    }

    GameState toGameState() {
        return new GameState(
                userId,
                gameId,
                difficulty,
                fromJson(originalGrid, new TypeReference<List<List<Integer>>>() {}),
                fromJson(currentGrid, new TypeReference<List<List<Integer>>>() {}),
                fromJson(candidates, new TypeReference<List<List<List<Integer>>>>() {}),
                timeSpentSeconds,
                status
        );
    }

    void applyUpdate(GameUpdateRequest request) {
        setCurrentGrid(toJson(request.currentGrid()));
        setCandidates(toJson(request.candidates()));
        setTimeSpentSeconds(request.timeSpentSeconds());
        setStatus(Boolean.TRUE.equals(request.isComplete()) ? "SOLVED" : "IN_PROGRESS");
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
