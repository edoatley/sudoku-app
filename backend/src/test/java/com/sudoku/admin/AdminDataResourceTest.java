package com.sudoku.admin;

import com.sudoku.game.persistence.GameItem;
import com.sudoku.game.web.GameState;
import com.sudoku.player.persistence.PlayerItem;
import com.sudoku.player.web.PlayerProfile;
import com.sudoku.web.DataListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Behaviour moved verbatim from the former DevDataResource (never had its own test).
class AdminDataResourceTest {

    private AdminDataResource resource;
    private DynamoDbTable<GameItem> gamesTable;
    private DynamoDbTable<PlayerItem> playersTable;

    @BeforeEach
    void setUp() throws Exception {
        resource = new AdminDataResource();
        gamesTable = mock(DynamoDbTable.class);
        playersTable = mock(DynamoDbTable.class);
        setField("gamesTable", gamesTable);
        setField("playersTable", playersTable);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = AdminDataResource.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(resource, value);
    }

    @Test
    void listGames_mapsItemsToGameStateAndWrapsInDataListResponse() {
        GameItem item = mock(GameItem.class);
        GameState state = mock(GameState.class);
        when(item.toGameState()).thenReturn(state);
        stubScan(gamesTable, item);

        DataListResponse<GameState> response = resource.listGames();

        assertEquals(List.of(state), response.items());
    }

    @Test
    void listGames_emptyTable_returnsEmptyList() {
        stubScan(gamesTable);

        DataListResponse<GameState> response = resource.listGames();

        assertEquals(List.of(), response.items());
    }

    @Test
    void listPlayers_mapsItemsToPlayerProfileAndWrapsInDataListResponse() {
        PlayerItem item = mock(PlayerItem.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(item.toPlayerProfile()).thenReturn(profile);
        stubScan(playersTable, item);

        DataListResponse<PlayerProfile> response = resource.listPlayers();

        assertEquals(List.of(profile), response.items());
    }

    @SuppressWarnings("unchecked")
    private <T> void stubScan(DynamoDbTable<T> table, T... items) {
        PageIterable<T> pageIterable = mock(PageIterable.class);
        SdkIterable<T> sdkIterable = mock(SdkIterable.class);
        when(table.scan()).thenReturn(pageIterable);
        when(pageIterable.items()).thenReturn(sdkIterable);
        when(sdkIterable.stream()).thenReturn(Stream.of(items));
    }
}
