package com.sudoku.game;

import com.sudoku.dto.GameState;
import com.sudoku.dto.GameUpdateRequest;
import com.sudoku.puzzle.SudokuService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@ApplicationScoped
public class GameServiceImpl implements GameService {

    @Inject
    SudokuService sudokuService;

    @Inject
    GameRepository gameRepository;

    @Override
    public GameState createGame(String userId, String difficulty) {
        var puzzle = sudokuService.generatePuzzle(difficulty);
        List<List<List<Integer>>> emptyCandidates = IntStream.range(0, 9)
                .mapToObj(r -> IntStream.range(0, 9)
                        .mapToObj(c -> List.<Integer>of())
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());

        GameState gameState = new GameState(
                userId,
                UUID.randomUUID().toString(),
                puzzle.difficulty(),
                puzzle.originalGrid(),
                puzzle.originalGrid().stream().map(row -> row.stream().collect(Collectors.toList())).collect(Collectors.toList()),
                emptyCandidates,
                0,
                "IN_PROGRESS"
        );
        gameRepository.save(gameState);
        return gameState;
    }

    @Override
    public GameState createGameFromExistingGrid(String userId, List<List<Integer>> originalGrid) {
        List<List<List<Integer>>> emptyCandidates = IntStream.range(0, 9)
                .mapToObj(r -> IntStream.range(0, 9)
                        .mapToObj(c -> List.<Integer>of())
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());

        GameState gameState = new GameState(
                userId,
                UUID.randomUUID().toString(),
                "imported",
                originalGrid,
                originalGrid.stream().map(row -> row.stream().collect(Collectors.toList())).collect(Collectors.toList()),
                emptyCandidates,
                0,
                "IN_PROGRESS"
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
}
