package com.sudoku.developer;

import com.sudoku.puzzle.web.PuzzleResponse;
import com.sudoku.puzzle.hint.NakedPairStrategy;
import com.sudoku.puzzle.hint.FullHouseStrategy;
import com.sudoku.puzzle.hint.HintStrategy;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// @spec PG-DEV-001
// @spec PG-DEV-001, PG-DEV-004
class DevResourceTest {

    private DevResource devResource;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        Instance<HintStrategy> instance = mock(Instance.class);
        when(instance.stream()).thenReturn(Stream.of(new FullHouseStrategy(), new NakedPairStrategy()));
        devResource = new DevResource(instance);
    }

    @Test
    void knownSlug_returns200WithCorrectRank() {
        Response response = devResource.hintDemo("naked-pair");

        assertEquals(200, response.getStatus());
        PuzzleResponse body = (PuzzleResponse) response.getEntity();
        assertEquals(new NakedPairStrategy().getDifficultyRank(), body.minRank());
    }

    @Test
    void knownSlug_returns200WithGrid() {
        Response response = devResource.hintDemo("full-house");

        assertEquals(200, response.getStatus());
        PuzzleResponse body = (PuzzleResponse) response.getEntity();
        assertNotNull(body.originalGrid());
        assertEquals(9, body.originalGrid().size());
    }

    @Test
    void unknownSlug_returns404() {
        Response response = devResource.hintDemo("does-not-exist");

        assertEquals(404, response.getStatus());
    }

    @Test
    void missingTechnique_returns400() {
        Response response = devResource.hintDemo(null);

        assertEquals(400, response.getStatus());
    }

    @Test
    void blankTechnique_returns400() {
        Response response = devResource.hintDemo("  ");

        assertEquals(400, response.getStatus());
    }
}
