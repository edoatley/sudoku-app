package com.sudoku.admin.persistence;

import com.sudoku.admin.AdminDataRepository;
import com.sudoku.game.persistence.GameItem;
import com.sudoku.game.web.GameState;
import com.sudoku.player.persistence.PlayerItem;
import com.sudoku.player.web.PlayerProfile;
import io.quarkus.arc.lookup.LookupUnlessProperty;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.List;

/**
 * DynamoDB-backed admin data browser (AWS adapter). Full table scans — acceptable given the low
 * data volumes of this personal project and the restricted admin audience.
 *
 * <p>Selected when {@code sudoku.persistence} is anything other than {@code firestore}. See
 * {@code GameRepositoryProducer} for the {@code @Typed}/lookup rationale.
 *
 * @spec UM-GCP-010
 */
@ApplicationScoped
@Typed(DynamoDbAdminDataRepository.class)
@LookupUnlessProperty(name = "sudoku.persistence", stringValue = "firestore", lookupIfMissing = true)
public class DynamoDbAdminDataRepository implements AdminDataRepository {

    @Inject
    DynamoDbEnhancedClient enhancedClient;

    @ConfigProperty(name = "sudoku.dynamodb.table-name")
    String gamesTableName;

    @ConfigProperty(name = "sudoku.dynamodb.players-table-name")
    String playersTableName;

    private DynamoDbTable<GameItem> gamesTable;
    private DynamoDbTable<PlayerItem> playersTable;

    @PostConstruct
    void init() {
        gamesTable = enhancedClient.table(gamesTableName, TableSchema.fromBean(GameItem.class));
        playersTable = enhancedClient.table(playersTableName, TableSchema.fromBean(PlayerItem.class));
    }

    @Override
    public List<GameState> findAllGames() {
        return gamesTable.scan().items().stream()
                .map(GameItem::toGameState)
                .toList();
    }

    @Override
    public List<PlayerProfile> findAllPlayers() {
        return playersTable.scan().items().stream()
                .map(PlayerItem::toPlayerProfile)
                .toList();
    }
}
