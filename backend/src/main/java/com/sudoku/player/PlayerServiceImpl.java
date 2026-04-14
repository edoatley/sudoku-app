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
 * must go through a dedicated update flow rather than being overwritten on every login.
 */
@ApplicationScoped
public class PlayerServiceImpl implements PlayerService {

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
                    now,
                    now
            );
            return playerRepository.upsert(newProfile);
        });
    }
}
