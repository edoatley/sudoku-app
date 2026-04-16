package com.sudoku.player;

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
    private String createdAt;
    private String updatedAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("userId")
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    static PlayerItem from(PlayerProfile profile) {
        PlayerItem item = new PlayerItem();
        item.setUserId(profile.userId());
        item.setEmail(profile.email() != null ? profile.email() : "");
        item.setDisplayName(profile.displayName() != null ? profile.displayName() : "");
        item.setCreatedAt(profile.createdAt());
        item.setUpdatedAt(profile.updatedAt());
        return item;
    }

    public PlayerProfile toPlayerProfile() {
        return new PlayerProfile(userId, email, displayName, createdAt, updatedAt);
    }
}
