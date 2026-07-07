package com.sudoku.leaderboard;

import com.sudoku.leaderboard.persistence.LeaderboardItem;
import com.sudoku.leaderboard.web.LeaderboardEntry;
import com.sudoku.leaderboard.web.LeaderboardResponse;
import com.sudoku.player.persistence.PlayerItem;
import com.sudoku.player.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LeaderboardServiceImpl} ranking and join logic.
 */
// @spec LT-BE-014, LT-BE-015, LT-BE-016, LT-BE-017, LT-BE-018
class LeaderboardServiceImplTest {

    private LeaderboardRepository leaderboardRepository;
    private PlayerRepository playerRepository;
    private LeaderboardServiceImpl leaderboardService;

    @BeforeEach
    void setUp() {
        leaderboardRepository = mock(LeaderboardRepository.class);
        playerRepository = mock(PlayerRepository.class);
        leaderboardService = new LeaderboardServiceImpl(leaderboardRepository, playerRepository);
    }

    private static PlayerItem player(String userId, String displayName, String avatarKey) {
        PlayerItem p = new PlayerItem();
        p.setUserId(userId);
        p.setDisplayName(displayName);
        p.setAvatarKey(avatarKey);
        return p;
    }

    private static LeaderboardItem aggregate(String userId, int totalWins, int totalGames,
                                              int totalScore, int totalElapsedSeconds) {
        LeaderboardItem item = new LeaderboardItem();
        item.setUserId(userId);
        item.setTotalWins(totalWins);
        item.setTotalGames(totalGames);
        item.setTotalScore(totalScore);
        item.setTotalElapsedSeconds(totalElapsedSeconds);
        return item;
    }

    // @spec LT-BE-015
    @Test
    void getLeaderboard_ranksByAvgScoreDescending() {
        LeaderboardItem highScorer = aggregate("user-a", 5, 5, 1000, 1500); // avgScore=200
        LeaderboardItem lowScorer  = aggregate("user-b", 5, 5, 500,  1500); // avgScore=100

        when(leaderboardRepository.findAll()).thenReturn(List.of(lowScorer, highScorer));
        when(playerRepository.findAll()).thenReturn(List.of(
                player("user-a", "Alice", "Person"),
                player("user-b", "Bob", "DirectionsRun")
        ));

        LeaderboardResponse response = leaderboardService.getLeaderboard();

        assertEquals(2, response.entries().size());
        assertEquals("user-a", response.entries().get(0).userId());
        assertEquals(1, response.entries().get(0).rank());
        assertEquals("user-b", response.entries().get(1).userId());
        assertEquals(2, response.entries().get(1).rank());
    }

    // @spec LT-BE-016
    @Test
    void getLeaderboard_tiesInAvgScore_rankedByFasterAvgTime() {
        // Both score avgScore=200; user-a is faster (avg 200s vs 300s)
        LeaderboardItem faster = aggregate("user-a", 5, 5, 1000, 1000); // avg=200s
        LeaderboardItem slower = aggregate("user-b", 5, 5, 1000, 1500); // avg=300s

        when(leaderboardRepository.findAll()).thenReturn(List.of(slower, faster));
        when(playerRepository.findAll()).thenReturn(List.of(
                player("user-a", "Alice", "Person"),
                player("user-b", "Bob", "Person")
        ));

        LeaderboardResponse response = leaderboardService.getLeaderboard();

        assertEquals("user-a", response.entries().get(0).userId());
        assertEquals("user-b", response.entries().get(1).userId());
    }

    // @spec LT-BE-017
    @Test
    void getLeaderboard_zeroWinPlayers_rankedBelowWinners() {
        LeaderboardItem winner   = aggregate("user-a", 3, 4, 600, 900); // avgScore=200
        LeaderboardItem noWins   = aggregate("user-b", 0, 5, 0,  0);    // avgScore=0

        when(leaderboardRepository.findAll()).thenReturn(List.of(noWins, winner));
        when(playerRepository.findAll()).thenReturn(List.of(
                player("user-a", "Alice", "Person"),
                player("user-b", "Bob", "Person")
        ));

        LeaderboardResponse response = leaderboardService.getLeaderboard();

        assertEquals("user-a", response.entries().get(0).userId());
        assertEquals("user-b", response.entries().get(1).userId());
    }

    // @spec LT-BE-017
    @Test
    void getLeaderboard_multipleZeroWinPlayers_rankedByTotalGamesDescending() {
        LeaderboardItem morePlayed  = aggregate("user-a", 0, 5, 0, 0);
        LeaderboardItem fewerPlayed = aggregate("user-b", 0, 2, 0, 0);

        when(leaderboardRepository.findAll()).thenReturn(List.of(fewerPlayed, morePlayed));
        when(playerRepository.findAll()).thenReturn(List.of(
                player("user-a", "Alice", "Person"),
                player("user-b", "Bob", "Person")
        ));

        LeaderboardResponse response = leaderboardService.getLeaderboard();

        assertEquals("user-a", response.entries().get(0).userId());
        assertEquals("user-b", response.entries().get(1).userId());
    }

    // @spec LT-BE-018
    @Test
    void getLeaderboard_entryIncludesDisplayNameAndAvatarFromPlayerProfile() {
        when(leaderboardRepository.findAll()).thenReturn(
                List.of(aggregate("user-a", 3, 3, 600, 900))
        );
        when(playerRepository.findAll()).thenReturn(
                List.of(player("user-a", "Ed", "SportsBasketball"))
        );

        LeaderboardEntry entry = leaderboardService.getLeaderboard().entries().get(0);

        assertEquals("Ed", entry.displayName());
        assertEquals("SportsBasketball", entry.avatarKey());
    }

    // @spec LT-BE-015
    @Test
    void getLeaderboard_avgScoreIsRoundedTotalScoreOverWins() {
        // totalScore=190, totalWins=3 → 190/3=63.33 → round=63
        when(leaderboardRepository.findAll()).thenReturn(
                List.of(aggregate("user-a", 3, 3, 190, 900))
        );
        when(playerRepository.findAll()).thenReturn(
                List.of(player("user-a", "Ed", "Person"))
        );

        LeaderboardEntry entry = leaderboardService.getLeaderboard().entries().get(0);

        assertEquals(63, entry.avgScore());
    }

    // @spec LT-BE-018
    @Test
    void getLeaderboard_avgElapsedSecondsIsRoundedTotalOverWins() {
        // totalElapsedSeconds=700, totalWins=3 → 700/3=233.33 → round=233
        when(leaderboardRepository.findAll()).thenReturn(
                List.of(aggregate("user-a", 3, 3, 600, 700))
        );
        when(playerRepository.findAll()).thenReturn(
                List.of(player("user-a", "Ed", "Person"))
        );

        LeaderboardEntry entry = leaderboardService.getLeaderboard().entries().get(0);

        assertEquals(233, entry.avgElapsedSeconds());
    }

    // @spec LT-BE-018
    @Test
    void getLeaderboard_zeroWinPlayer_avgElapsedSecondsIsNull() {
        when(leaderboardRepository.findAll()).thenReturn(
                List.of(aggregate("user-a", 0, 2, 0, 0))
        );
        when(playerRepository.findAll()).thenReturn(
                List.of(player("user-a", "Ed", "Person"))
        );

        LeaderboardEntry entry = leaderboardService.getLeaderboard().entries().get(0);

        assertNull(entry.avgElapsedSeconds());
    }

    @Test
    void getLeaderboard_emptyRepository_returnsEmptyList() {
        when(leaderboardRepository.findAll()).thenReturn(List.of());
        when(playerRepository.findAll()).thenReturn(List.of());

        LeaderboardResponse response = leaderboardService.getLeaderboard();

        assertTrue(response.entries().isEmpty());
    }
}
