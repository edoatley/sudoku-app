package com.sudoku.leaderboard.persistence;

import com.sudoku.leaderboard.LeaderboardRepository;
import jakarta.annotation.PostConstruct;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// @spec LT-BE-007, LT-BE-008, LT-BE-009, LT-BE-010, LT-BE-011, LT-BE-012, LT-BE-013
@ApplicationScoped
@UnlessBuildProperty(name = "sudoku.persistence", stringValue = "firestore", enableIfMissing = true)
public class DynamoDbLeaderboardRepository implements LeaderboardRepository {

    @Inject
    DynamoDbEnhancedClient enhancedClient;

    @Inject
    DynamoDbClient dynamoDbClient;

    @ConfigProperty(name = "sudoku.dynamodb.leaderboard-table-name")
    String tableName;

    private DynamoDbTable<LeaderboardItem> table;

    @PostConstruct
    void init() {
        table = enhancedClient.table(tableName, TableSchema.fromBean(LeaderboardItem.class));
    }

    // @spec LT-BE-008, LT-BE-009, LT-BE-010, LT-BE-011, LT-BE-012
    @Override
    public void updateOnSolve(String userId, String difficulty, int elapsedSeconds, int score, String outcome) {
        boolean won = "won".equals(outcome);
        String bestTimeAttr = bestTimeAttribute(difficulty);
        String now = Instant.now().toString();

        // Build the ADD expressions for atomic counters
        StringBuilder updateExpr = new StringBuilder("ADD totalGames :one");
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":one", AttributeValue.fromN("1"));
        values.put(":updatedAt", AttributeValue.fromS(now));
        values.put(":elapsed", AttributeValue.fromN(String.valueOf(elapsedSeconds)));

        if (won) {
            updateExpr.append(", totalWins :one, totalScore :score, totalElapsedSeconds :elapsed");
            values.put(":score", AttributeValue.fromN(String.valueOf(score)));

            // Conditional best-time update: set only if no existing value or new is faster
            // @spec LT-BE-012: use if_not_exists to initialise, then min-like comparison via two-pass
            updateExpr.append(", ").append(bestTimeAttr).append(" :elapsed");
            // We use a conditional expression to only update best time if the new time is faster
        }

        updateExpr.append(" SET updatedAt = :updatedAt");

        UpdateItemRequest.Builder requestBuilder = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("userId", AttributeValue.fromS(userId)));

        if (won) {
            // @spec LT-BE-012: update best time only if not set or new time is faster
            updateExpr = new StringBuilder("ADD totalGames :one, totalWins :one, totalScore :score, totalElapsedSeconds :elapsed")
                    .append(" SET updatedAt = :updatedAt, ")
                    .append(bestTimeAttr).append(" = if_not_exists(").append(bestTimeAttr).append(", :bigNum)");

            values.put(":bigNum", AttributeValue.fromN("999999"));

            requestBuilder
                    .updateExpression(updateExpr.toString())
                    .expressionAttributeValues(values);
            dynamoDbClient.updateItem(requestBuilder.build());

            // Second pass: update best time only if new value is smaller
            Map<String, AttributeValue> vals2 = Map.of(
                    ":elapsed", AttributeValue.fromN(String.valueOf(elapsedSeconds))
            );
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("userId", AttributeValue.fromS(userId)))
                    .updateExpression("SET " + bestTimeAttr + " = :elapsed")
                    .conditionExpression(bestTimeAttr + " > :elapsed")
                    .expressionAttributeValues(vals2)
                    .build());
        } else {
            // Non-win: only increment totalGames
            requestBuilder
                    .updateExpression("ADD totalGames :one SET updatedAt = :updatedAt")
                    .expressionAttributeValues(Map.of(
                            ":one", AttributeValue.fromN("1"),
                            ":updatedAt", AttributeValue.fromS(now)
                    ));
            dynamoDbClient.updateItem(requestBuilder.build());
        }
    }

    @Override
    public List<LeaderboardItem> findAll() {
        return table.scan().items().stream().toList();
    }

    private static String bestTimeAttribute(String difficulty) {
        return switch (difficulty) {
            case "easy"     -> "bestEasySeconds";
            case "hard"     -> "bestHardSeconds";
            case "imported" -> "bestImportedSeconds";
            default         -> "bestMediumSeconds";
        };
    }
}
