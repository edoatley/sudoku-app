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

    private static final String USER_ID = "test-user-123";

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
        gameService = new GameServiceImpl(sudokuService, gameRepository);
    }

    @Test
    void createGame_generatesPuzzleAndPersistsGameState() {
        when(sudokuService.generatePuzzle("easy")).thenReturn(new PuzzleResponse(GRID, null, "easy"));

        GameState result = gameService.createGame(USER_ID, "easy");

        assertEquals(USER_ID, result.userId());
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
        when(sudokuService.generatePuzzle(anyString())).thenReturn(new PuzzleResponse(GRID, null, "medium"));

        GameState game1 = gameService.createGame(USER_ID, "medium");
        GameState game2 = gameService.createGame(USER_ID, "medium");

        assertNotEquals(game1.gameId(), game2.gameId());
    }

    @Test
    void loadGame_whenFound_returnsGameState() {
        String gameId = "test-id-123";
        GameState expected = new GameState(USER_ID, gameId, "easy", GRID, null, GRID, List.of(), 42, "IN_PROGRESS", 0, null, null);
        when(gameRepository.findById(USER_ID, gameId)).thenReturn(Optional.of(expected));

        GameState result = gameService.loadGame(USER_ID, gameId);

        assertEquals(expected, result);
    }

    @Test
    void loadGame_whenNotFound_throwsNotFoundException() {
        when(gameRepository.findById(USER_ID, "unknown")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> gameService.loadGame(USER_ID, "unknown"));
    }

    @Test
    void createGameFromExistingGrid_usesSuppliedException_andPersists() {
        when(sudokuService.solveGrid(GRID)).thenReturn(Optional.empty());

        GameState result = gameService.createGameFromExistingGrid(USER_ID, GRID);

        assertEquals(USER_ID, result.userId());
        assertNotNull(result.gameId());
        assertEquals("imported", result.difficulty());
        assertEquals(GRID, result.originalGrid());
        assertEquals(GRID, result.currentGrid());
        assertNull(result.solutionGrid());
        assertEquals(0, result.timeSpentSeconds());
        assertEquals("IN_PROGRESS", result.status());
        assertEquals(9, result.candidates().size());
        verify(sudokuService, never()).generatePuzzle(anyString());
        verify(sudokuService).solveGrid(GRID);
        verify(gameRepository).save(any(GameState.class));
    }

    @Test
    void createGameFromExistingGrid_whenSolvable_storesSolution() {
        List<List<Integer>> solution = List.of(
                List.of(5, 3, 4, 6, 7, 8, 9, 1, 2),
                List.of(6, 7, 2, 1, 9, 5, 3, 4, 8),
                List.of(1, 9, 8, 3, 4, 2, 5, 6, 7),
                List.of(8, 5, 9, 7, 6, 1, 4, 2, 3),
                List.of(4, 2, 6, 8, 5, 3, 7, 9, 1),
                List.of(7, 1, 3, 9, 2, 4, 8, 5, 6),
                List.of(9, 6, 1, 5, 3, 7, 2, 8, 4),
                List.of(2, 8, 7, 4, 1, 9, 6, 3, 5),
                List.of(3, 4, 5, 2, 8, 6, 1, 7, 9)
        );
        when(sudokuService.solveGrid(GRID)).thenReturn(Optional.of(solution));

        GameState result = gameService.createGameFromExistingGrid(USER_ID, GRID);

        assertEquals(solution, result.solutionGrid());
        verify(sudokuService).solveGrid(GRID);
    }

    @Test
    void createGameFromExistingGrid_generatesUniqueGameIds() {
        when(sudokuService.solveGrid(GRID)).thenReturn(Optional.empty());

        GameState game1 = gameService.createGameFromExistingGrid(USER_ID, GRID);
        GameState game2 = gameService.createGameFromExistingGrid(USER_ID, GRID);

        assertNotEquals(game1.gameId(), game2.gameId());
    }

    @Test
    void updateGame_delegatesToRepository() {
        String gameId = "test-id-456";
        GameUpdateRequest request = new GameUpdateRequest(GRID, List.of(), 120, false, null);

        gameService.updateGame(USER_ID, gameId, request);

        verify(gameRepository).update(eq(USER_ID), eq(gameId), eq(request));
    }
    @Test
    void findInProgress_whenFound_returnsGame() {
        GameState inProgress = new GameState(USER_ID, "game-ip-1", "easy", GRID, null, GRID, List.of(), 30, "IN_PROGRESS", 0, null, null);
        when(gameRepository.findInProgress(USER_ID)).thenReturn(Optional.of(inProgress));

        Optional<GameState> result = gameService.findInProgress(USER_ID);

        assertTrue(result.isPresent());
        assertEquals("IN_PROGRESS", result.get().status());
        assertEquals("game-ip-1", result.get().gameId());
    }

    @Test
    void findInProgress_whenNone_returnsEmpty() {
        when(gameRepository.findInProgress(USER_ID)).thenReturn(Optional.empty());

        Optional<GameState> result = gameService.findInProgress(USER_ID);

        assertTrue(result.isEmpty());
    }

}
