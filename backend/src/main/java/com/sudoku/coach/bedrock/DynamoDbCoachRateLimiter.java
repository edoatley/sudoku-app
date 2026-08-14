package com.sudoku.coach.bedrock;

import io.quarkus.arc.lookup.LookupUnlessProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * DynamoDB-backed coach rate limiter (AWS adapter). Atomically increments a per-user/per-minute
 * counter under a conditional expression, so the limit is enforced without a read-modify-write race.
 *
 * <p>Selected when {@code sudoku.persistence} is anything other than {@code firestore}.
 *
 * @spec SC-RL-003, SC-RL-004, SC-RL-011
 */
@ApplicationScoped
@Typed(DynamoDbCoachRateLimiter.class)
@LookupUnlessProperty(name = "sudoku.persistence", stringValue = "firestore", lookupIfMissing = true)
public class DynamoDbCoachRateLimiter implements CoachRateLimiter {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(DynamoDbCoachRateLimiter.class);
    private static final DateTimeFormatter WINDOW_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Inject
    DynamoDbClient dynamoDbClient;

    @ConfigProperty(name = "coach.rate-limit.table-name")
    String tableName;

    @ConfigProperty(name = "coach.rate-limit.per-minute")
    int perMinuteLimit;

    @Override
    public boolean tryConsume(String userId) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String window = now.format(WINDOW_FMT);
        // TTL = 2 minutes after the start of the current minute window
        long ttl = now.withSecond(0).withNano(0).plusMinutes(2).toEpochSecond();

        try {
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(
                            "userId", AttributeValue.fromS(userId),
                            "window", AttributeValue.fromS(window)
                    ))
                    .updateExpression("ADD callCount :one SET expiresAt = if_not_exists(expiresAt, :ttl)")
                    .conditionExpression("attribute_not_exists(callCount) OR callCount < :limit")
                    .expressionAttributeValues(Map.of(
                            ":one", AttributeValue.fromN("1"),
                            ":ttl", AttributeValue.fromN(String.valueOf(ttl)),
                            ":limit", AttributeValue.fromN(String.valueOf(perMinuteLimit))
                    ))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        } catch (Exception e) {
            LOG.warnf("Rate limit check failed for userId=%s, failing open: %s", userId, e.getMessage());
            return true;
        }
    }
}
