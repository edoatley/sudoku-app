package com.sudoku.admin;

import com.sudoku.web.DataListResponse;
import com.sudoku.game.web.GameState;
import com.sudoku.game.persistence.GameItem;
import com.sudoku.player.persistence.PlayerItem;
import com.sudoku.player.web.PlayerProfile;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.List;

/**
 * Admin-only read endpoints that expose all DynamoDB table contents for the in-app data
 * browser. Reachable in production by members of the {@code administrators} Cognito group
 * (see {@link AdminAuthorizationFilter}).
 *
 * <p>Full table scans are used here — acceptable given the low data volumes of this personal
 * project and the restricted admin audience.
 */
@ApplicationScoped
@Path("/admin/data")
@Produces(MediaType.APPLICATION_JSON)
@AdminOnly
public class AdminDataResource {

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

    /**
     * Returns all game records as standard JSON (grids as integer arrays, not DynamoDB strings).
     *
     * @return all games from the SudokuGames table
     */
    @GET
    @Path("/games")
    public DataListResponse<GameState> listGames() {
        List<GameState> games = gamesTable.scan().items().stream()
                .map(GameItem::toGameState)
                .toList();
        return new DataListResponse<>(games);
    }

    /**
     * Returns all player profiles.
     *
     * @return all players from the SudokuPlayers table
     */
    @GET
    @Path("/players")
    public DataListResponse<PlayerProfile> listPlayers() {
        List<PlayerProfile> players = playersTable.scan().items().stream()
                .map(PlayerItem::toPlayerProfile)
                .toList();
        return new DataListResponse<>(players);
    }
}
