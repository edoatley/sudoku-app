package com.sudoku.game;

import com.sudoku.domain.CandidatesGrid;
import com.sudoku.domain.Grid;
import com.sudoku.puzzle.web.BoardRequest;
import com.sudoku.game.web.GameHistoryResponse;
import com.sudoku.game.web.GameState;
import com.sudoku.game.web.GameUpdateRequest;
import com.sudoku.puzzle.web.ValidationResponse;
import com.sudoku.leaderboard.LeaderboardRepository;
import com.sudoku.puzzle.SudokuService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;

/**
 * Orchestrates the lifecycle of a player's Sudoku game session.
 *
 * <p>Handles the transition from a freshly generated (or externally imported) puzzle into
 * a persisted {@link com.sudoku.game.web.GameState}, and routes ongoing save/load operations
 * through the {@link GameRepository}. Keeping this logic here rather than in the resource
 * layer ensures the HTTP boundary remains thin and testable independently of persistence.
 *
 * <p>Enforces the single-active-game invariant: any existing IN_PROGRESS game for a player
 * is automatically transitioned to ABANDONED before a new game is created, ensuring only
 * one game is ever IN_PROGRESS per player at a time.
 */
@ApplicationScoped
public class GameServiceImpl implements GameService {

    private final SudokuService sudokuService;
    private final GameRepository gameRepository;
    private final LeaderboardRepository leaderboardRepository;

    @Inject
    public GameServiceImpl(SudokuService sudokuService,
                           GameRepository gameRepository,
                           LeaderboardRepository leaderboardRepository) {
        this.sudokuService = sudokuService;
        this.gameRepository = gameRepository;
        this.leaderboardRepository = leaderboardRepository;
    }

    @Override
    public GameState createGame(String userId, String difficulty) {
        abandonAnyInProgressGame(userId);
        var puzzle = sudokuService.generatePuzzle(difficulty);
        CandidatesGrid emptyCandidates = emptyCandidates();

        GameState gameState = new GameState(
                userId,
                UUID.randomUUID().toString(),
                puzzle.difficulty(),
                puzzle.originalGrid(),
                puzzle.solutionGrid(),
                puzzle.originalGrid(),
                emptyCandidates,
                0,
                GameStatus.IN_PROGRESS.getValue(),
                0,
                Instant.now().toString(),
                null,
                0
        );
        gameRepository.save(gameState);
        return gameState;
    }

    // @spec DT-SVC-003
    @Override
    public GameState createGameFromExistingGrid(String userId, Grid originalGrid) {
        // Stage 1: duplicate clue check — reuses existing validateByDuplicates path
        // @spec GL-BE-003, GL-BE-004
        ValidationResponse validation = sudokuService.validatePuzzle(new BoardRequest(originalGrid));
        if (!validation.isValid()) {
            throw new DuplicateDigitsException();
        }

        // Stage 2: uniqueness — must have exactly one solution
        // @spec GL-BE-005, GL-BE-006
        Optional<Grid> solution = sudokuService.solveGrid(originalGrid);
        if (solution.isEmpty()) {
            throw new PuzzleHasNoSolutionException();
        }
        if (!sudokuService.hasSingleSolution(originalGrid)) {
            throw new PuzzleHasMultipleSolutionsException();
        }

        // Validation passed — now safe to abandon any existing in-progress game
        abandonAnyInProgressGame(userId);

        GameState gameState = new GameState(
                userId,
                UUID.randomUUID().toString(),
                "imported",
                originalGrid,
                solution.get(),
                originalGrid,
                emptyCandidates(),
                0,
                GameStatus.IN_PROGRESS.getValue(),
                0,
                Instant.now().toString(),
                null,
                0
        );
        gameRepository.save(gameState);
        return gameState;
    }

    // @spec GL-API-004 — GameNotFoundException thrown by repository if not found
    @Override
    public GameState loadGame(String userId, String gameId) {
        return gameRepository.findById(userId, gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId)); // defensive; repo throws first
    }


    @Override
    public Optional<GameState> findInProgress(String userId) {
        return gameRepository.findInProgress(userId);
    }

    // @spec LT-BE-005, LT-BE-006
    @Override
    public void updateGame(String userId, String gameId, GameUpdateRequest request) {
        if (Boolean.TRUE.equals(request.isComplete())) {
            GameState game = gameRepository.findById(userId, gameId)
                    .orElseThrow(() -> new GameNotFoundException(gameId));
            gameRepository.update(userId, gameId, request);
            int score = ScoringConstants.computeScore(game.difficulty(), request.timeSpentSeconds(), game.hintsUsed());
            leaderboardRepository.updateOnSolve(userId, game.difficulty(), request.timeSpentSeconds(), score, "won");
        } else {
            gameRepository.update(userId, gameId, request);
        }
    }

    // @spec GH-SVC-001
    @Override
    public GameHistoryResponse getGameHistory(String userId, int limit) {
        return new GameHistoryResponse(gameRepository.findHistory(userId, limit));
    }

    /**
     * Enforces the single-active-game invariant: if the player already has an IN_PROGRESS
     * game when they start a new one, that prior game is marked ABANDONED before the new
     * game is persisted. This is done server-side so the client never has to orchestrate
     * the transition.
     */
    private CandidatesGrid emptyCandidates() {
        List<List<List<Integer>>> rows = new ArrayList<>(UNIT_SIZE);
        for (int r = 0; r < UNIT_SIZE; r++) {
            List<List<Integer>> row = new ArrayList<>(UNIT_SIZE);
            for (int c = 0; c < UNIT_SIZE; c++) {
                row.add(List.of());
            }
            rows.add(Collections.unmodifiableList(row));
        }
        return CandidatesGrid.of(Collections.unmodifiableList(rows));
    }

    private void abandonAnyInProgressGame(String userId) {
        gameRepository.findInProgress(userId)
                .ifPresent(existing -> gameRepository.abandonGame(userId, existing.gameId()));
    }
}
