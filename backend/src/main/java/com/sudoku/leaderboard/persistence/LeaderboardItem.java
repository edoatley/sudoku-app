package com.sudoku.leaderboard.persistence;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

// @spec LT-BE-007
@DynamoDbBean
public class LeaderboardItem {

    private String userId;
    private int totalWins;
    private int totalGames;
    private int totalScore;
    private int totalElapsedSeconds;
    private Integer bestEasySeconds;
    private Integer bestMediumSeconds;
    private Integer bestHardSeconds;
    private Integer bestImportedSeconds;
    private String updatedAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("userId")
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public int getTotalWins() { return totalWins; }
    public void setTotalWins(int totalWins) { this.totalWins = totalWins; }

    public int getTotalGames() { return totalGames; }
    public void setTotalGames(int totalGames) { this.totalGames = totalGames; }

    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }

    public int getTotalElapsedSeconds() { return totalElapsedSeconds; }
    public void setTotalElapsedSeconds(int totalElapsedSeconds) { this.totalElapsedSeconds = totalElapsedSeconds; }

    public Integer getBestEasySeconds() { return bestEasySeconds; }
    public void setBestEasySeconds(Integer bestEasySeconds) { this.bestEasySeconds = bestEasySeconds; }

    public Integer getBestMediumSeconds() { return bestMediumSeconds; }
    public void setBestMediumSeconds(Integer bestMediumSeconds) { this.bestMediumSeconds = bestMediumSeconds; }

    public Integer getBestHardSeconds() { return bestHardSeconds; }
    public void setBestHardSeconds(Integer bestHardSeconds) { this.bestHardSeconds = bestHardSeconds; }

    public Integer getBestImportedSeconds() { return bestImportedSeconds; }
    public void setBestImportedSeconds(Integer bestImportedSeconds) { this.bestImportedSeconds = bestImportedSeconds; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
