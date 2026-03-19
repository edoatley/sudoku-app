package com.sudoku.game;

import com.sudoku.dto.GameState;
import com.sudoku.dto.GameUpdateRequest;
import com.sudoku.dto.PuzzleResponse;
import com.sudoku.puzzle.SudokuService;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GameServiceImplTest {

    private SudokuService sudokuService;
    private GameRepository gameRepository;
    private GameServiceImpl gameService;

    private static final List<List<Integer>> GRID = List.of(
            List.of(5, 3, 0, 0, 7, 0, 0, 0, 0),
            List.of(6, 0, 0, 1, 9, 5, 0, 0, 0),
            List.of(0, 9, 8, 0, 0, 0, 0, 6, 0),
            List.of(8, 0, 0, 0, 6, 0, 0, 0, 3),
            List.of(4, 0, 0, 8, 0, 3, 0, 0, 1),
            List.of(7, 0, 0, 0, 2, 0, 0, 0, 6),
            List.of(0, 6, 0, 0, 0, 0, 2, 8, 0),
            List.of(0, 0, 0, 4, 1, 9, 0, 0, 5),
            List.of(0, 0, 0, 0, 8, 0, 0, 7, 9)
    );

    @BeforeEach
    void setUp() {
        sudokuService = mock(SudokuService.class);
        gameRepository = mock(GameRepository.class);
        gameService = new GameServiceImpl();
        gameService.sudokuService = sudokuService;
        gameService.gameRepository = gameRepository;
    }

    @Test
    void createGame_generatesPuzzleAndPersistsGameState() {
        when(sudokuService.generatePuzzle("easy")).thenReturn(new PuzzleResponse(GRID, "easy"));

        GameState result = gameService.createGame("easy");

        assertNotNull(result.gameId());
        assertEquals("easy", result.difficulty());
        assertEquals(GRID, result.originalGrid());
        assertEquals(0, result.timeSpentSeconds());
        assertEquals("IN_PROGRESS", result.status());
        assertNotNull(result.candidates());
        assertEquals(9, result.candidates().size());
        verify(gameRepository).save(any(GameState.class));
    }

    @Test
    void createGame_generatesUniqueGameIds() {
        when(sudokuService.generatePuzzle(anyString())).thenReturn(new PuzzleResponse(GRID, "medium"));

        GameState game1 = gameService.createGame("medium");
        GameState game2 = gameService.createGame("medium");

        assertNotEquals(game1.gameId(), game2.gameId());
    }

    @Test
    void loadGame_whenFound_returnsGameState() {
        String gameId = "test-id-123";
        GameState expected = new GameState(gameId, "easy", GRID, GRID, List.of(), 42, "IN_PROGRESS");
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(expected));

        GameState result = gameService.loadGame(gameId);

        assertEquals(expected, result);
    }

    @Test
    void loadGame_whenNotFound_throwsNotFoundException() {
        when(gameRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> gameService.loadGame("unknown"));
    }

    @Test
    void updateGame_delegatesToRepository() {
        String gameId = "test-id-456";
        GameUpdateRequest request = new GameUpdateRequest(GRID, List.of(), 120, false);

        gameService.updateGame(gameId, request);

        verify(gameRepository).update(eq(gameId), eq(request));
    }
}
