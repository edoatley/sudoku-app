package com.sudoku.game;

import com.sudoku.dto.GameState;
import com.sudoku.dto.GameUpdateRequest;
import com.sudoku.puzzle.SudokuService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;

/**
 * Orchestrates the lifecycle of a player's Sudoku game session.
 *
 * <p>Handles the transition from a freshly generated (or externally imported) puzzle into
 * a persisted {@link com.sudoku.dto.GameState}, and routes ongoing save/load operations
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

    @Inject
    public GameServiceImpl(SudokuService sudokuService, GameRepository gameRepository) {
        this.sudokuService = sudokuService;
        this.gameRepository = gameRepository;
    }

    @Override
    public GameState createGame(String userId, String difficulty) {
        abandonAnyInProgressGame(userId);
        var puzzle = sudokuService.generatePuzzle(difficulty);
        List<List<List<Integer>>> emptyCandidates = IntStream.range(0, UNIT_SIZE)
                .mapToObj(r -> IntStream.range(0, UNIT_SIZE)
                        .mapToObj(c -> List.<Integer>of())
                        .toList())
                .toList();

        GameState gameState = new GameState(
                userId,
                UUID.randomUUID().toString(),
                puzzle.difficulty(),
                puzzle.originalGrid(),
                puzzle.solutionGrid(),
                puzzle.originalGrid().stream().map(List::copyOf).toList(),
                emptyCandidates,
                0,
                GameStatus.IN_PROGRESS.getValue(),
                0,
                Instant.now().toString(),
                null
        );
        gameRepository.save(gameState);
        return gameState;
    }

    @Override
    public GameState createGameFromExistingGrid(String userId, List<List<Integer>> originalGrid) {
        abandonAnyInProgressGame(userId);
        List<List<List<Integer>>> emptyCandidates = IntStream.range(0, UNIT_SIZE)
                .mapToObj(r -> IntStream.range(0, UNIT_SIZE)
                        .mapToObj(c -> List.<Integer>of())
                        .toList())
                .toList();

        // Attempt to solve the imported grid so validation can do precise cell-by-cell checking.
        // If the grid has no valid solution (e.g. OCR error), falls back to null and validation
        // gracefully degrades to duplicate-detection only.
        List<List<Integer>> solution = sudokuService.solveGrid(originalGrid).orElse(null);

        GameState gameState = new GameState(
                userId,
                UUID.randomUUID().toString(),
                GameStatus.IMPORTED.getValue(),
                originalGrid,
                solution,
                originalGrid.stream().map(List::copyOf).toList(),
                emptyCandidates,
                0,
                GameStatus.IN_PROGRESS.getValue(),
                0,
                Instant.now().toString(),
                null
        );
        gameRepository.save(gameState);
        return gameState;
    }

    @Override
    public GameState loadGame(String userId, String gameId) {
        return gameRepository.findById(userId, gameId)
                .orElseThrow(() -> new NotFoundException("Game not found: " + gameId));
    }


    @Override
    public Optional<GameState> findInProgress(String userId) {
        return gameRepository.findInProgress(userId);
    }

    @Override
    public void updateGame(String userId, String gameId, GameUpdateRequest request) {
        gameRepository.update(userId, gameId, request);
    }

    /**
     * Enforces the single-active-game invariant: if the player already has an IN_PROGRESS
     * game when they start a new one, that prior game is marked ABANDONED before the new
     * game is persisted. This is done server-side so the client never has to orchestrate
     * the transition.
     */
    private void abandonAnyInProgressGame(String userId) {
        gameRepository.findInProgress(userId)
                .ifPresent(existing -> gameRepository.abandonGame(userId, existing.gameId()));
    }
}
