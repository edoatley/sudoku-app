package com.sudoku.game;

import com.sudoku.dto.GameState;
import com.sudoku.dto.GameUpdateRequest;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
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

    @Override
    public Optional<GameState> findById(String userId, String gameId) {
        GameItem item = table.getItem(Key.builder()
                .partitionValue(userId)
                .sortValue(gameId)
                .build());
        return Optional.ofNullable(item).map(GameItem::toGameState);
    }

    @Override
    public void update(String userId, String gameId, GameUpdateRequest request) {
        GameItem existing = table.getItem(Key.builder()
                .partitionValue(userId)
                .sortValue(gameId)
                .build());
        if (existing == null) {
            return;
        }
        existing.applyUpdate(request);
        table.updateItem(existing);
    }
}
