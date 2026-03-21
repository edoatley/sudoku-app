package com.sudoku.player;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

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
