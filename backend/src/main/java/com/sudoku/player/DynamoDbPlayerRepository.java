package com.sudoku.player;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

/**
 * DynamoDB-backed persistence for player profiles.
 *
 * <p>Player profiles are keyed solely by {@code userId} (the Cognito sub claim), making
 * reads and upserts O(1) by partition key. The profile is created lazily on first login
 * rather than at registration time, so no separate sign-up flow is required.
 */
@ApplicationScoped
public class DynamoDbPlayerRepository implements PlayerRepository {

    @Inject
    DynamoDbEnhancedClient enhancedClient;

    @ConfigProperty(name = "sudoku.dynamodb.players-table-name")
    String tableName;

    private DynamoDbTable<PlayerItem> table;

    @PostConstruct
    void init() {
        table = enhancedClient.table(tableName, TableSchema.fromBean(PlayerItem.class));
    }

    @Override
    public Optional<PlayerProfile> findById(String userId) {
        PlayerItem item = table.getItem(Key.builder().partitionValue(userId).build());
        return Optional.ofNullable(item).map(PlayerItem::toPlayerProfile);
    }

    @Override
    public PlayerProfile upsert(PlayerProfile profile) {
        table.putItem(PlayerItem.from(profile));
        return profile;
    }
}
