package com.sudoku.admin;

import com.sudoku.game.web.GameState;
import com.sudoku.player.web.PlayerProfile;

import java.util.List;

/**
 * Read port for the admin data browser. Selected at runtime by {@code sudoku.persistence}
 * ({@code DynamoDbAdminDataRepository} on AWS, {@code FirestoreAdminDataRepository} on GCP)
 * via {@link com.sudoku.admin.persistence.AdminDataRepositoryProducer}. Each adapter owns its own
 * item-to-domain mapping so callers see identical response shapes on both clouds.
 *
 * @spec UM-GCP-010
 */
public interface AdminDataRepository {

    /** @return every game record, mapped to the API {@link GameState} shape. */
    List<GameState> findAllGames();

    /** @return every player profile. */
    List<PlayerProfile> findAllPlayers();
}
