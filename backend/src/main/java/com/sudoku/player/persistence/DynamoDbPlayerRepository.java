package com.sudoku.player.persistence;

import com.sudoku.player.web.PlayerProfile;
import com.sudoku.player.PlayerRepository;
import jakarta.annotation.PostConstruct;
import io.quarkus.arc.lookup.LookupUnlessProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB-backed persistence for player profiles.
 *
 * <p>Player profiles are keyed solely by {@code userId} (the Cognito sub claim), making
 * reads and upserts O(1) by partition key. The profile is created lazily on first login
 * rather than at registration time, so no separate sign-up flow is required.
 */
@ApplicationScoped
@Typed(DynamoDbPlayerRepository.class)
@LookupUnlessProperty(name = "sudoku.persistence", stringValue = "firestore", lookupIfMissing = true)
public class DynamoDbPlayerRepository implements PlayerRepository {

    @Inject
    DynamoDbEnhancedClient enhancedClient;

    @Inject
    DynamoDbClient dynamoDbClient;

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

    // @spec LT-BE-014
    @Override
    public List<PlayerItem> findAll() {
        return table.scan().items().stream().toList();
    }

    // @spec SC-RL-002, SC-RL-005
    @Override
    public void incrementCoachTokens(String userId, long tokensUsed, String currentMonth) {
        if (tokensUsed <= 0) return;
        Map<String, AttributeValue> key = Map.of(
                "userId", AttributeValue.fromS(userId)
        );
        try {
            // Conditional update: only increment if the stored month matches the current month
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression("SET coachTokenMonth = :month, coachTokensUsedThisMonth = if_not_exists(coachTokensUsedThisMonth, :zero) + :tokens")
                    .conditionExpression("coachTokenMonth = :month OR attribute_not_exists(coachTokenMonth)")
                    .expressionAttributeValues(Map.of(
                            ":month", AttributeValue.fromS(currentMonth),
                            ":tokens", AttributeValue.fromN(String.valueOf(tokensUsed)),
                            ":zero", AttributeValue.fromN("0")
                    ))
                    .build());
        } catch (ConditionalCheckFailedException e) {
            // Month has rolled over: reset the counter to just this request's tokens.
            // Concurrent requests at the exact month-boundary can both enter this catch block and race
            // on the unconditional write below; the last writer wins and the other's tokens are dropped
            // from the counter. Impact: at most ~300 tokens under-counted at the start of a new month.
            // This is accepted — adding a second conditional here would not eliminate the race and would
            // add complexity for negligible benefit (<$0.001 per occurrence).
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression("SET coachTokenMonth = :month, coachTokensUsedThisMonth = :tokens")
                    .expressionAttributeValues(Map.of(
                            ":month", AttributeValue.fromS(currentMonth),
                            ":tokens", AttributeValue.fromN(String.valueOf(tokensUsed))
                    ))
                    .build());
        }
    }
}
