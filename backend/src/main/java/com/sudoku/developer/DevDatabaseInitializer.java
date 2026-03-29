package com.sudoku.developer;

import com.sudoku.game.GameItem;
import com.sudoku.player.PlayerItem;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

/**
 * Creates DynamoDB tables on startup in dev mode so that {@code ./mvnw quarkus:dev}
 * works without any manual setup. Only active when the {@code dev} Quarkus profile
 * is active (i.e. the {@code %dev.} config prefix applies).
 */
@ApplicationScoped
public class DevDatabaseInitializer {

    private static final Logger LOG = Logger.getLogger(DevDatabaseInitializer.class);

    @Inject
    DynamoDbEnhancedClient enhancedClient;

    @ConfigProperty(name = "sudoku.dynamodb.table-name")
    String gamesTableName;

    @ConfigProperty(name = "sudoku.dynamodb.players-table-name")
    String playersTableName;

    @ConfigProperty(name = "quarkus.profile")
    String profile;

    void onStart(@Observes StartupEvent event) {
        if (!"dev".equals(profile)) {
            return;
        }
        createTable(enhancedClient.table(gamesTableName, TableSchema.fromBean(GameItem.class)), gamesTableName);
        createTable(enhancedClient.table(playersTableName, TableSchema.fromBean(PlayerItem.class)), playersTableName);
    }

    private void createTable(DynamoDbTable<?> table, String tableName) {
        try {
            table.createTable();
            LOG.infof("Created DynamoDB table: %s", tableName);
        } catch (ResourceInUseException e) {
            LOG.infof("DynamoDB table already exists: %s", tableName);
        }
    }
}
