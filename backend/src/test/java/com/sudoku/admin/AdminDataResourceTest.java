package com.sudoku.admin;

import com.sudoku.game.web.GameState;
import com.sudoku.player.web.PlayerProfile;
import com.sudoku.web.DataListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Thin resource: delegates to the AdminDataRepository port and wraps results in DataListResponse.
class AdminDataResourceTest {

    private AdminDataResource resource;
    private AdminDataRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        resource = new AdminDataResource();
        repository = mock(AdminDataRepository.class);
        Field field = AdminDataResource.class.getDeclaredField("repository");
        field.setAccessible(true);
        field.set(resource, repository);
    }

    @Test
    void listGames_wrapsRepositoryResultInDataListResponse() {
        GameState state = mock(GameState.class);
        when(repository.findAllGames()).thenReturn(List.of(state));

        DataListResponse<GameState> response = resource.listGames();

        assertEquals(List.of(state), response.items());
    }

    @Test
    void listGames_emptyResult_returnsEmptyList() {
        when(repository.findAllGames()).thenReturn(List.of());

        DataListResponse<GameState> response = resource.listGames();

        assertEquals(List.of(), response.items());
    }

    @Test
    void listPlayers_wrapsRepositoryResultInDataListResponse() {
        PlayerProfile profile = mock(PlayerProfile.class);
        when(repository.findAllPlayers()).thenReturn(List.of(profile));

        DataListResponse<PlayerProfile> response = resource.listPlayers();

        assertEquals(List.of(profile), response.items());
    }
}
