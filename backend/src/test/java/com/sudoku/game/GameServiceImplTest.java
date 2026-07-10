package com.sudoku.game;

import com.sudoku.domain.CandidatesGrid;
import com.sudoku.domain.Grid;
import com.sudoku.puzzle.web.BoardRequest;
import com.sudoku.puzzle.web.Coordinate;
import com.sudoku.game.web.GameState;
import com.sudoku.game.web.GameUpdateRequest;
import com.sudoku.game.web.PuzzleEvent;
import com.sudoku.puzzle.web.PuzzleResponse;
import com.sudoku.puzzle.web.ValidationResponse;
import com.sudoku.leaderboard.LeaderboardRepository;
import com.sudoku.puzzle.SudokuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// @spec GL-BE-001, GL-BE-002, GL-BE-003, GL-BE-004, GL-BE-005, GL-BE-006, GL-BE-010, GL-BE-011, GL-BE-012, GL-BE-020, GL-BE-021, GL-BE-022, GL-BE-030, GL-DATA-001, GL-DATA-002, GL-DATA-003, GL-DATA-004
class GameServiceImplTest {

    private SudokuService sudokuService;
    private GameRepository gameRepository;
    private LeaderboardRepository leaderboardRepository;
    private PuzzleEventLogger puzzleEventLogger;
    private GameServiceImpl gameService;

    private static final String USER_ID = "test-user-123";

    private static final Grid GRID = Grid.of(List.of(
            List.of(5, 3, 0, 0, 7, 0, 0, 0, 0),
            List.of(6, 0, 0, 1, 9, 5, 0, 0, 0),
            List.of(0, 9, 8, 0, 0, 0, 0, 6, 0),
            List.of(8, 0, 0, 0, 6, 0, 0, 0, 3),
            List.of(4, 0, 0, 8, 0, 3, 0, 0, 1),
            List.of(7, 0, 0, 0, 2, 0, 0, 0, 6),
            List.of(0, 6, 0, 0, 0, 0, 2, 8, 0),
            List.of(0, 0, 0, 4, 1, 9, 0, 0, 5),
            List.of(0, 0, 0, 0, 8, 0, 0, 7, 9)
    ));

    private static final Grid SOLUTION = Grid.of(List.of(
            List.of(5, 3, 4, 6, 7, 8, 9, 1, 2),
            List.of(6, 7, 2, 1, 9, 5, 3, 4, 8),
            List.of(1, 9, 8, 3, 4, 2, 5, 6, 7),
            List.of(8, 5, 9, 7, 6, 1, 4, 2, 3),
            List.of(4, 2, 6, 8, 5, 3, 7, 9, 1),
            List.of(7, 1, 3, 9, 2, 4, 8, 5, 6),
            List.of(9, 6, 1, 5, 3, 7, 2, 8, 4),
            List.of(2, 8, 7, 4, 1, 9, 6, 3, 5),
            List.of(3, 4, 5, 2, 8, 6, 1, 7, 9)
    ));

    private static final ValidationResponse VALID_RESPONSE =
            new ValidationResponse(true, false, List.of());

    private static CandidatesGrid emptyCandidates() {
        return CandidatesGrid.of(List.of(
                List.of(List.of()), List.of(List.of()), List.of(List.of()),
                List.of(List.of()), List.of(List.of()), List.of(List.of()),
                List.of(List.of()), List.of(List.of()), List.of(List.of())
        ));
    }

    @BeforeEach
    void setUp() {
        sudokuService = mock(SudokuService.class);
        gameRepository = mock(GameRepository.class);
        leaderboardRepository = mock(LeaderboardRepository.class);
        puzzleEventLogger = mock(PuzzleEventLogger.class);
        gameService = new GameServiceImpl(sudokuService, gameRepository, leaderboardRepository, puzzleEventLogger);
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
        assertEquals(9, result.candidates().rows().size());
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
        GameState expected = new GameState(USER_ID, gameId, "easy", GRID, null, GRID, emptyCandidates(), 42, "IN_PROGRESS", 0, null, null, 0);
        when(gameRepository.findById(USER_ID, gameId)).thenReturn(Optional.of(expected));

        GameState result = gameService.loadGame(USER_ID, gameId);

        assertEquals(expected, result);
    }

    @Test
    void loadGame_whenNotFound_throwsGameNotFoundException() {
        when(gameRepository.findById(USER_ID, "unknown")).thenReturn(Optional.empty());

        assertThrows(GameNotFoundException.class, () -> gameService.loadGame(USER_ID, "unknown"));
    }

    @Test
    void createGameFromExistingGrid_withValidPuzzle_persistsGameWithSolution() {
        when(sudokuService.validatePuzzle(any(BoardRequest.class))).thenReturn(VALID_RESPONSE);
        when(sudokuService.solveGrid(GRID)).thenReturn(Optional.of(SOLUTION));
        when(sudokuService.hasSingleSolution(GRID)).thenReturn(true);

        GameState result = gameService.createGameFromExistingGrid(USER_ID, GRID);

        assertEquals(USER_ID, result.userId());
        assertNotNull(result.gameId());
        assertEquals("imported", result.difficulty());
        assertEquals(GRID, result.originalGrid());
        assertEquals(GRID, result.currentGrid());
        assertEquals(SOLUTION, result.solutionGrid());
        assertEquals(0, result.timeSpentSeconds());
        assertEquals("IN_PROGRESS", result.status());
        assertEquals(9, result.candidates().rows().size());
        verify(sudokuService, never()).generatePuzzle(anyString());
        verify(gameRepository).save(any(GameState.class));
    }

    @Test
    void createGameFromExistingGrid_whenSolvable_storesSolution() {
        when(sudokuService.validatePuzzle(any(BoardRequest.class))).thenReturn(VALID_RESPONSE);
        when(sudokuService.solveGrid(GRID)).thenReturn(Optional.of(SOLUTION));
        when(sudokuService.hasSingleSolution(GRID)).thenReturn(true);

        GameState result = gameService.createGameFromExistingGrid(USER_ID, GRID);

        assertEquals(SOLUTION, result.solutionGrid());
    }

    @Test
    void createGameFromExistingGrid_generatesUniqueGameIds() {
        when(sudokuService.validatePuzzle(any(BoardRequest.class))).thenReturn(VALID_RESPONSE);
        when(sudokuService.solveGrid(GRID)).thenReturn(Optional.of(SOLUTION));
        when(sudokuService.hasSingleSolution(GRID)).thenReturn(true);

        GameState game1 = gameService.createGameFromExistingGrid(USER_ID, GRID);
        GameState game2 = gameService.createGameFromExistingGrid(USER_ID, GRID);

        assertNotEquals(game1.gameId(), game2.gameId());
    }

    // @spec GL-BE-004
    @Test
    void createGameFromExistingGrid_withDuplicateDigits_throwsDuplicateDigitsException() {
        ValidationResponse invalidResponse = new ValidationResponse(
                false, false, List.of(new Coordinate(0, 0), new Coordinate(0, 1)));
        when(sudokuService.validatePuzzle(any(BoardRequest.class))).thenReturn(invalidResponse);

        assertThrows(DuplicateDigitsException.class,
                () -> gameService.createGameFromExistingGrid(USER_ID, GRID));
        verify(gameRepository, never()).save(any());
    }

    // @spec GL-BE-005
    @Test
    void createGameFromExistingGrid_withNoSolution_throwsPuzzleHasNoSolutionException() {
        when(sudokuService.validatePuzzle(any(BoardRequest.class))).thenReturn(VALID_RESPONSE);
        when(sudokuService.solveGrid(GRID)).thenReturn(Optional.empty());

        assertThrows(PuzzleHasNoSolutionException.class,
                () -> gameService.createGameFromExistingGrid(USER_ID, GRID));
        verify(gameRepository, never()).save(any());
    }

    // @spec GL-BE-006
    @Test
    void createGameFromExistingGrid_withMultipleSolutions_throwsPuzzleHasMultipleSolutionsException() {
        when(sudokuService.validatePuzzle(any(BoardRequest.class))).thenReturn(VALID_RESPONSE);
        when(sudokuService.solveGrid(GRID)).thenReturn(Optional.of(SOLUTION));
        when(sudokuService.hasSingleSolution(GRID)).thenReturn(false);

        assertThrows(PuzzleHasMultipleSolutionsException.class,
                () -> gameService.createGameFromExistingGrid(USER_ID, GRID));
        verify(gameRepository, never()).save(any());
    }

    @Test
    void createGameFromExistingGrid_whenInvalid_doesNotAbandonExistingGame() {
        ValidationResponse invalidResponse = new ValidationResponse(
                false, false, List.of(new Coordinate(0, 0)));
        when(sudokuService.validatePuzzle(any(BoardRequest.class))).thenReturn(invalidResponse);
        GameState existing = new GameState(USER_ID, "old-game-id", "easy", GRID, null, GRID,
                emptyCandidates(), 90, "IN_PROGRESS", 0, "2026-01-01T10:00:00Z", null, 0);
        when(gameRepository.findInProgress(USER_ID)).thenReturn(Optional.of(existing));

        assertThrows(InvalidPuzzleException.class,
                () -> gameService.createGameFromExistingGrid(USER_ID, GRID));
        verify(gameRepository, never()).abandonGame(anyString(), anyString());
        verify(gameRepository, never()).save(any());
    }

    @Test
    void updateGame_delegatesToRepository() {
        String gameId = "test-id-456";
        GameUpdateRequest request = new GameUpdateRequest(GRID, emptyCandidates(), 120, false, null);

        gameService.updateGame(USER_ID, gameId, request);

        verify(gameRepository).update(eq(USER_ID), eq(gameId), eq(request));
    }

    @Test
    void updateGame_withoutEvents_doesNotLoadGameOrLog() {
        // @spec GL-BE-045 — no events: normal save, no game load, no puzzle-play logging
        String gameId = "test-id-456";
        GameUpdateRequest request = new GameUpdateRequest(GRID, emptyCandidates(), 120, false, null);

        gameService.updateGame(USER_ID, gameId, request);

        verify(gameRepository, never()).findById(anyString(), anyString());
        verifyNoInteractions(puzzleEventLogger);
    }

    @Test
    void updateGame_withEvents_loadsGameAndLogsWithSolution() {
        // @spec GL-BE-040 — events present: load the game and delegate to the logger with its solution
        String gameId = "test-id-456";
        List<PuzzleEvent> events = List.of(
                new PuzzleEvent("NUMBER", 0, 0, 5, null, 1000L, null, null, null, null, null, null));
        GameUpdateRequest request = new GameUpdateRequest(GRID, emptyCandidates(), 120, false, null, events);
        GameState game = new GameState(USER_ID, gameId, "easy", GRID, SOLUTION, GRID,
                emptyCandidates(), 120, "IN_PROGRESS", 0, "2026-01-01T10:00:00Z", null, 0);
        when(gameRepository.findById(USER_ID, gameId)).thenReturn(Optional.of(game));

        gameService.updateGame(USER_ID, gameId, request);

        verify(gameRepository).update(eq(USER_ID), eq(gameId), eq(request));
        verify(puzzleEventLogger).log(eq(gameId), eq(USER_ID), eq(SOLUTION), eq(events));
    }
    @Test
    void createGame_whenInProgressGameExists_abandonsItBeforeCreatingNew() {
        GameState existing = new GameState(USER_ID, "old-game-id", "easy", GRID, null, GRID,
                emptyCandidates(), 90, "IN_PROGRESS", 0, "2026-01-01T10:00:00Z", null, 0);
        when(sudokuService.generatePuzzle("medium")).thenReturn(new PuzzleResponse(GRID, null, "medium"));
        when(gameRepository.findInProgress(USER_ID)).thenReturn(Optional.of(existing));

        gameService.createGame(USER_ID, "medium");

        InOrder order = inOrder(gameRepository);
        order.verify(gameRepository).findInProgress(USER_ID);
        order.verify(gameRepository).abandonGame(USER_ID, "old-game-id");
        order.verify(gameRepository).save(any(GameState.class));
    }

    @Test
    void createGame_whenNoInProgressGame_doesNotCallAbandon() {
        when(sudokuService.generatePuzzle("easy")).thenReturn(new PuzzleResponse(GRID, null, "easy"));
        when(gameRepository.findInProgress(USER_ID)).thenReturn(Optional.empty());

        gameService.createGame(USER_ID, "easy");

        verify(gameRepository, never()).abandonGame(anyString(), anyString());
        verify(gameRepository).save(any(GameState.class));
    }

    @Test
    void createGameFromExistingGrid_whenInProgressGameExists_abandonsItBeforeCreatingNew() {
        GameState existing = new GameState(USER_ID, "old-imported-id", "easy", GRID, null, GRID,
                emptyCandidates(), 30, "IN_PROGRESS", 0, "2026-01-01T09:00:00Z", null, 0);
        when(gameRepository.findInProgress(USER_ID)).thenReturn(Optional.of(existing));
        when(sudokuService.validatePuzzle(any(BoardRequest.class))).thenReturn(VALID_RESPONSE);
        when(sudokuService.solveGrid(GRID)).thenReturn(Optional.of(SOLUTION));
        when(sudokuService.hasSingleSolution(GRID)).thenReturn(true);

        gameService.createGameFromExistingGrid(USER_ID, GRID);

        InOrder order = inOrder(gameRepository);
        order.verify(gameRepository).findInProgress(USER_ID);
        order.verify(gameRepository).abandonGame(USER_ID, "old-imported-id");
        order.verify(gameRepository).save(any(GameState.class));
    }

    @Test
    void createGameFromExistingGrid_whenNoInProgressGame_doesNotCallAbandon() {
        when(gameRepository.findInProgress(USER_ID)).thenReturn(Optional.empty());
        when(sudokuService.validatePuzzle(any(BoardRequest.class))).thenReturn(VALID_RESPONSE);
        when(sudokuService.solveGrid(GRID)).thenReturn(Optional.of(SOLUTION));
        when(sudokuService.hasSingleSolution(GRID)).thenReturn(true);

        gameService.createGameFromExistingGrid(USER_ID, GRID);

        verify(gameRepository, never()).abandonGame(anyString(), anyString());
    }

    @Test
    void findInProgress_whenFound_returnsGame() {
        GameState inProgress = new GameState(USER_ID, "game-ip-1", "easy", GRID, null, GRID, emptyCandidates(), 30, "IN_PROGRESS", 0, null, null, 0);
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
