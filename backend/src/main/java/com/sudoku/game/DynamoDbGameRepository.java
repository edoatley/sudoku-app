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

@ApplicationScoped
public class DynamoDbGameRepository implements GameRepository {

    static final String TABLE_NAME = "SudokuGames";

    @Inject
    DynamoDbEnhancedClient enhancedClient;

    private DynamoDbTable<GameItem> table;

    @PostConstruct
    void init() {
        table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(GameItem.class));
    }

    @Override
    public void save(GameState gameState) {
        table.putItem(GameItem.from(gameState));
    }

    @Override
    public Optional<GameState> findById(String gameId) {
        GameItem item = table.getItem(Key.builder().partitionValue(gameId).build());
        return Optional.ofNullable(item).map(GameItem::toGameState);
    }

    @Override
    public void update(String gameId, GameUpdateRequest request) {
        GameItem existing = table.getItem(Key.builder().partitionValue(gameId).build());
        if (existing == null) {
            return;
        }
        existing.applyUpdate(request);
        table.updateItem(existing);
    }
}
