package com.sudoku.leaderboard;

import com.sudoku.dto.LeaderboardResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

// @spec LT-API-001, LT-API-002
@Path("/leaderboard")
@Produces(MediaType.APPLICATION_JSON)
public class LeaderboardResource {

    private final LeaderboardService leaderboardService;

    @Inject
    public LeaderboardResource(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GET
    public LeaderboardResponse getLeaderboard() {
        return leaderboardService.getLeaderboard();
    }
}
