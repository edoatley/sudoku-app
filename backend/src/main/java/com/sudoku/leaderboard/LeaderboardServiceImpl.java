package com.sudoku.leaderboard;

import com.sudoku.leaderboard.persistence.LeaderboardItem;
import com.sudoku.leaderboard.web.LeaderboardEntry;
import com.sudoku.leaderboard.web.LeaderboardResponse;
import com.sudoku.player.persistence.PlayerItem;
import com.sudoku.player.PlayerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// @spec LT-BE-014, LT-BE-015, LT-BE-016, LT-BE-017, LT-BE-018
@ApplicationScoped
public class LeaderboardServiceImpl implements LeaderboardService {

    private final LeaderboardRepository leaderboardRepository;
    private final PlayerRepository playerRepository;

    @Inject
    public LeaderboardServiceImpl(LeaderboardRepository leaderboardRepository,
                                   PlayerRepository playerRepository) {
        this.leaderboardRepository = leaderboardRepository;
        this.playerRepository = playerRepository;
    }

    @Override
    public LeaderboardResponse getLeaderboard() {
        List<LeaderboardItem> aggregates = leaderboardRepository.findAll();
        Map<String, PlayerItem> playerIndex = playerRepository.findAll().stream()
                .collect(Collectors.toMap(PlayerItem::getUserId, p -> p));

        Comparator<LeaderboardItem> rankOrder = Comparator
                // @spec LT-BE-017: zero-win players rank below players with wins
                .comparingInt((LeaderboardItem i) -> i.getTotalWins() > 0 ? 0 : 1)
                // @spec LT-BE-015: primary — avgScore descending
                .thenComparingDouble(i -> -avgScore(i))
                // @spec LT-BE-016: secondary tie-break — avgElapsedSeconds ascending
                .thenComparingDouble(LeaderboardServiceImpl::avgElapsedSeconds)
                // @spec LT-BE-017: zero-win tie-break — totalGames descending
                .thenComparingInt(i -> -i.getTotalGames());

        List<LeaderboardItem> sorted = aggregates.stream().sorted(rankOrder).toList();

        List<LeaderboardEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            LeaderboardItem agg = sorted.get(i);
            PlayerItem player = playerIndex.get(agg.getUserId());
            String displayName = player != null ? player.getDisplayName() : agg.getUserId();
            String avatarKey   = player != null ? player.getAvatarKey() : null;

            int avgScoreVal = (int) Math.round(avgScore(agg));
            Integer avgElapsedVal = agg.getTotalWins() > 0
                    ? (int) Math.round(avgElapsedSeconds(agg))
                    : null;

            entries.add(new LeaderboardEntry(
                    agg.getUserId(),
                    displayName,
                    avatarKey,
                    i + 1,
                    agg.getTotalWins(),
                    agg.getTotalGames(),
                    avgScoreVal,
                    avgElapsedVal,
                    bestTimes(agg)
            ));
        }

        return new LeaderboardResponse(entries);
    }

    private static double avgScore(LeaderboardItem item) {
        return item.getTotalWins() > 0 ? (double) item.getTotalScore() / item.getTotalWins() : 0.0;
    }

    private static double avgElapsedSeconds(LeaderboardItem item) {
        return item.getTotalWins() > 0 ? (double) item.getTotalElapsedSeconds() / item.getTotalWins() : Double.MAX_VALUE;
    }

    private static Map<String, Integer> bestTimes(LeaderboardItem item) {
        Map<String, Integer> map = new HashMap<>();
        if (item.getBestEasySeconds() != null)     map.put("easy",     item.getBestEasySeconds());
        if (item.getBestMediumSeconds() != null)   map.put("medium",   item.getBestMediumSeconds());
        if (item.getBestHardSeconds() != null)     map.put("hard",     item.getBestHardSeconds());
        if (item.getBestImportedSeconds() != null) map.put("imported", item.getBestImportedSeconds());
        return map;
    }
}
