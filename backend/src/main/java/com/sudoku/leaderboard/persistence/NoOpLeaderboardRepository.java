package com.sudoku.leaderboard.persistence;

import com.sudoku.leaderboard.LeaderboardRepository;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;

import java.util.List;

/**
 * No-op leaderboard repository for the GCP (firestore) build.
 *
 * <p>The leaderboard is out of scope for the games + player-profile slice, but the game-solve path
 * calls {@link #updateOnSolve} and the app must boot with a {@code LeaderboardRepository} bean. This
 * stub lets games work end-to-end (including solving) on GCP without persisting leaderboard data; a
 * Firestore leaderboard adapter is a later arrow.
 */
@ApplicationScoped
@Typed(NoOpLeaderboardRepository.class)
@LookupIfProperty(name = "sudoku.persistence", stringValue = "firestore")
public class NoOpLeaderboardRepository implements LeaderboardRepository {

    @Override
    public void updateOnSolve(String userId, String difficulty, int elapsedSeconds, int score, String outcome) {
        // intentionally no-op — leaderboard is not part of the GCP games slice
    }

    @Override
    public List<LeaderboardItem> findAll() {
        return List.of();
    }
}
