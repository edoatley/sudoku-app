package com.sudoku.admin.persistence;

import com.sudoku.game.persistence.GameItem;
import com.sudoku.game.web.GameState;
import com.sudoku.player.persistence.PlayerItem;
import com.sudoku.player.web.PlayerProfile;
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

// Behaviour moved verbatim from the former AdminDataResource (DynamoDB scan + map).
class DynamoDbAdminDataRepositoryTest {

    private DynamoDbAdminDataRepository repo;
    private DynamoDbTable<GameItem> gamesTable;
    private DynamoDbTable<PlayerItem> playersTable;

    @BeforeEach
    void setUp() throws Exception {
        repo = new DynamoDbAdminDataRepository();
        gamesTable = mock(DynamoDbTable.class);
        playersTable = mock(DynamoDbTable.class);
        setField("gamesTable", gamesTable);
        setField("playersTable", playersTable);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = DynamoDbAdminDataRepository.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(repo, value);
    }

    @Test
    void findAllGames_mapsItemsToGameState() {
        GameItem item = mock(GameItem.class);
        GameState state = mock(GameState.class);
        when(item.toGameState()).thenReturn(state);
        stubScan(gamesTable, item);

        assertEquals(List.of(state), repo.findAllGames());
    }

    @Test
    void findAllGames_emptyTable_returnsEmptyList() {
        stubScan(gamesTable);

        assertEquals(List.of(), repo.findAllGames());
    }

    @Test
    void findAllPlayers_mapsItemsToPlayerProfile() {
        PlayerItem item = mock(PlayerItem.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        when(item.toPlayerProfile()).thenReturn(profile);
        stubScan(playersTable, item);

        assertEquals(List.of(profile), repo.findAllPlayers());
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
