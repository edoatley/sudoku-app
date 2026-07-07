package com.sudoku.player.persistence;

import com.sudoku.player.web.PlayerProfile;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * DynamoDB entity for a player profile. Kept separate from PlayerProfile (the REST DTO)
 * so the two concerns stay independent.
 */
@DynamoDbBean
public class PlayerItem {

    private String userId;
    private String email;
    private String displayName;
    private String avatarKey;
    private String createdAt;
    private String updatedAt;
    private Boolean aiCoachEnabled;
    private Long coachTokensUsedThisMonth;
    private String coachTokenMonth;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("userId")
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAvatarKey() { return avatarKey; }
    public void setAvatarKey(String avatarKey) { this.avatarKey = avatarKey; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public Boolean getAiCoachEnabled() { return aiCoachEnabled; }
    public void setAiCoachEnabled(Boolean aiCoachEnabled) { this.aiCoachEnabled = aiCoachEnabled; }

    public Long getCoachTokensUsedThisMonth() { return coachTokensUsedThisMonth; }
    public void setCoachTokensUsedThisMonth(Long coachTokensUsedThisMonth) { this.coachTokensUsedThisMonth = coachTokensUsedThisMonth; }

    public String getCoachTokenMonth() { return coachTokenMonth; }
    public void setCoachTokenMonth(String coachTokenMonth) { this.coachTokenMonth = coachTokenMonth; }

    static PlayerItem from(PlayerProfile profile) {
        PlayerItem item = new PlayerItem();
        item.setUserId(profile.userId());
        item.setEmail(profile.email() != null ? profile.email() : "");
        item.setDisplayName(profile.displayName() != null ? profile.displayName() : "");
        item.setAvatarKey(profile.avatarKey() != null ? profile.avatarKey() : "");
        item.setCreatedAt(profile.createdAt());
        item.setUpdatedAt(profile.updatedAt());
        item.setAiCoachEnabled(profile.aiCoachEnabled() != null ? profile.aiCoachEnabled() : Boolean.TRUE);
        item.setCoachTokensUsedThisMonth(profile.coachTokensUsedThisMonth() != null ? profile.coachTokensUsedThisMonth() : 0L);
        item.setCoachTokenMonth(profile.coachTokenMonth());
        return item;
    }

    public PlayerProfile toPlayerProfile() {
        String resolvedAvatarKey = (avatarKey != null && !avatarKey.isEmpty()) ? avatarKey : null;
        return new PlayerProfile(userId, email, displayName, resolvedAvatarKey, createdAt, updatedAt,
                aiCoachEnabled != null ? aiCoachEnabled : Boolean.TRUE,
                coachTokensUsedThisMonth != null ? coachTokensUsedThisMonth : 0L,
                coachTokenMonth);
    }
}
