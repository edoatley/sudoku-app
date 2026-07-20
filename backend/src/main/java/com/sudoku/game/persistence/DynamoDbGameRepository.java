package com.sudoku.game.persistence;

import com.sudoku.game.GameStatus;
import com.sudoku.game.GameNotFoundException;
import com.sudoku.game.GameRepository;
import com.sudoku.game.web.GameHistoryEntry;
import com.sudoku.game.web.GameState;
import com.sudoku.game.web.GameUpdateRequest;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * DynamoDB-backed persistence for Sudoku game sessions.
 *
 * <p>Each game is stored with the player's {@code userId} as the partition key and a
 * UUID {@code gameId} as the sort key, allowing efficient per-player queries and direct
 * key lookups. Grid data is serialised to JSON strings by {@link GameItem} before storage,
 * since DynamoDB has no native multi-dimensional list type.
 *
 * <p>Also enforces part of the single-active-game invariant via {@link #abandonGame},
 * which transitions an IN_PROGRESS game to ABANDONED when the player starts a new one.
 */
@ApplicationScoped
@UnlessBuildProperty(name = "sudoku.persistence", stringValue = "firestore", enableIfMissing = true)
public class DynamoDbGameRepository implements GameRepository {

    @Inject
    DynamoDbEnhancedClient enhancedClient;

    @ConfigProperty(name = "sudoku.dynamodb.table-name")
    String tableName;

    private DynamoDbTable<GameItem> table;

    @PostConstruct
    void init() {
        table = enhancedClient.table(tableName, TableSchema.fromBean(GameItem.class));
    }

    @Override
    public void save(GameState gameState) {
        table.putItem(GameItem.from(gameState));
    }

    // @spec AEH-EX-008
    @Override
    public Optional<GameState> findById(String userId, String gameId) {
        GameItem item = table.getItem(Key.builder()
                .partitionValue(userId)
                .sortValue(gameId)
                .build());
        if (item == null) {
            throw new GameNotFoundException(gameId);
        }
        return Optional.of(item.toGameState());
    }

    @Override
    public Optional<GameState> findInProgress(String userId) {
        return table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build()))
                .items()
                .stream()
                .filter(item -> GameStatus.IN_PROGRESS.getValue().equals(item.getStatus()))
                .findFirst()
                .map(GameItem::toGameState);
    }

    // @spec AEH-EX-009
    @Override
    public void update(String userId, String gameId, GameUpdateRequest request) {
        GameItem existing = table.getItem(Key.builder()
                .partitionValue(userId)
                .sortValue(gameId)
                .build());
        if (existing == null) {
            throw new GameNotFoundException(gameId);
        }
        existing.applyUpdate(request, Instant.now().toString());
        table.updateItem(existing);
    }

    // @spec GH-BE-001, GH-BE-004
    @Override
    public List<GameHistoryEntry> findHistory(String userId, int limit) {
        return table.query(QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build()))
                .items()
                .stream()
                .filter(item -> !GameStatus.IN_PROGRESS.getValue().equals(item.getStatus()))
                .filter(item -> item.getEndedAt() != null)
                .sorted(Comparator.comparing(GameItem::getEndedAt).reversed())
                .limit(limit)
                .map(item -> new GameHistoryEntry(
                        item.getGameId(),
                        item.getDifficulty(),
                        GameStatus.SOLVED.getValue().equals(item.getStatus()) ? "won" : "abandoned",
                        item.getEndedAt(),
                        item.getTimeSpentSeconds(),
                        item.getHintsUsed(),
                        item.getScore()))
                .toList();
    }

    @Override
    public void abandonGame(String userId, String gameId) {
        GameItem existing = table.getItem(Key.builder()
                .partitionValue(userId)
                .sortValue(gameId)
                .build());
        if (existing == null) {
            return;
        }
        existing.markAbandoned(Instant.now().toString());
        table.updateItem(existing);
    }
}
