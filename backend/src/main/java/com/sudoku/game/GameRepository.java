package com.sudoku.game;

import com.sudoku.dto.GameHistoryEntry;
import com.sudoku.dto.GameState;
import com.sudoku.dto.GameUpdateRequest;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for Sudoku game sessions.
 *
 * <p>Provides CRUD operations for game state, including querying for the active
 * IN_PROGRESS game per player and transitioning games to terminal states (SOLVED,
 * ABANDONED). Implementations are expected to use {@code userId} as the primary
 * partition key so that all per-player queries remain efficient.
 */
public interface GameRepository {

    void save(GameState gameState);

    /**
     * Finds a game by its composite key.
     *
     * @throws GameNotFoundException if no game exists for the given (userId, gameId) pair
     */
    Optional<GameState> findById(String userId, String gameId);

    Optional<GameState> findInProgress(String userId);

    void update(String userId, String gameId, GameUpdateRequest request);

    /**
     * Marks the specified game as ABANDONED with an endedAt timestamp.
     * Called server-side during game creation to enforce the single-active-game
     * invariant. If the game does not exist the call is a no-op.
     *
     * @param userId  the player's identity
     * @param gameId  the game to abandon
     */
    void abandonGame(String userId, String gameId);

    /**
     * Returns completed (SOLVED or ABANDONED) games for the user, ordered by
     * {@code endedAt} descending, capped at {@code limit}. IN_PROGRESS games are excluded.
     *
     * @spec GH-BE-001
     */
    List<GameHistoryEntry> findHistory(String userId, int limit);
}
