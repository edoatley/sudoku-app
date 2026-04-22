package com.sudoku.player;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

/**
 * Manages player profile creation and retrieval.
 *
 * <p>Player profiles are provisioned lazily: the first authenticated request triggers
 * profile creation using identity claims from the JWT (email, display name). Subsequent
 * requests return the existing profile without modification, so display-name changes
 * must go through the dedicated PATCH /players/me update flow.
 */
@ApplicationScoped
public class PlayerServiceImpl implements PlayerService {

    private static final int DISPLAY_NAME_MAX_LENGTH = 50;

    @Inject
    PlayerRepository playerRepository;

    @Override
    public PlayerProfile getOrCreateProfile(String userId, String email, String displayName) {
        return playerRepository.findById(userId).orElseGet(() -> {
            String now = Instant.now().toString();
            PlayerProfile newProfile = new PlayerProfile(
                    userId,
                    email != null ? email : "",
                    displayName != null ? displayName : "",
                    null,
                    now,
                    now
            );
            return playerRepository.upsert(newProfile);
        });
    }

    // @spec UM-BE-003, UM-BE-004, UM-BE-005, UM-BE-006
    @Override
    public PlayerProfile updateProfile(String userId, PlayerUpdateRequest request) {
        validateUpdateRequest(request);

        PlayerProfile existing = playerRepository.findById(userId)
                .orElseThrow(() -> new PlayerNotFoundException(userId));

        String updatedDisplayName = request.displayName() != null
                ? request.displayName().trim()
                : existing.displayName();
        String updatedAvatarKey = request.avatarKey() != null
                ? request.avatarKey()
                : existing.avatarKey();

        PlayerProfile updated = new PlayerProfile(
                existing.userId(),
                existing.email(),
                updatedDisplayName,
                updatedAvatarKey,
                existing.createdAt(),
                Instant.now().toString()
        );
        return playerRepository.upsert(updated);
    }

    private void validateUpdateRequest(PlayerUpdateRequest request) {
        boolean hasDisplayName = request.displayName() != null;
        boolean hasAvatarKey = request.avatarKey() != null;

        if (!hasDisplayName && !hasAvatarKey) {
            throw new InvalidPlayerUpdateException("At least one field (displayName, avatarKey) must be provided");
        }

        if (hasDisplayName) {
            String trimmed = request.displayName().trim();
            if (trimmed.isEmpty()) {
                throw new InvalidPlayerUpdateException("displayName must not be blank");
            }
            if (trimmed.length() > DISPLAY_NAME_MAX_LENGTH) {
                throw new InvalidPlayerUpdateException(
                        "displayName must not exceed " + DISPLAY_NAME_MAX_LENGTH + " characters");
            }
        }
    }
}
