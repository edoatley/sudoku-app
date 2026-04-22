package com.sudoku.player;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// @spec UM-BE-003, UM-BE-004, UM-BE-005, UM-BE-006
class PlayerServiceImplUpdateTest {

    private PlayerServiceImpl service;
    private PlayerRepository playerRepository;

    private static final PlayerProfile EXISTING = new PlayerProfile(
            "user-123", "user@example.com", "Alice", "SportsGolf", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"
    );

    @BeforeEach
    void setUp() throws Exception {
        service = new PlayerServiceImpl();
        playerRepository = mock(PlayerRepository.class);
        Field repoField = PlayerServiceImpl.class.getDeclaredField("playerRepository");
        repoField.setAccessible(true);
        repoField.set(service, playerRepository);
    }

    // @spec UM-BE-003
    @Test
    void updateProfile_displayNameOnly_updatesDisplayName() {
        when(playerRepository.findById("user-123")).thenReturn(Optional.of(EXISTING));
        when(playerRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        PlayerProfile result = service.updateProfile("user-123", new PlayerUpdateRequest("Bob", null));

        assertEquals("Bob", result.displayName());
        assertEquals("SportsGolf", result.avatarKey());       // unchanged
        assertEquals("user@example.com", result.email());     // unchanged
        assertEquals("2026-01-01T00:00:00Z", result.createdAt()); // unchanged
        assertNotNull(result.updatedAt());
    }

    // @spec UM-BE-003
    @Test
    void updateProfile_avatarKeyOnly_updatesAvatarKey() {
        when(playerRepository.findById("user-123")).thenReturn(Optional.of(EXISTING));
        when(playerRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        PlayerProfile result = service.updateProfile("user-123", new PlayerUpdateRequest(null, "Surfing"));

        assertEquals("Alice", result.displayName());  // unchanged
        assertEquals("Surfing", result.avatarKey());
    }

    // @spec UM-BE-003
    @Test
    void updateProfile_bothFields_updatesBoth() {
        when(playerRepository.findById("user-123")).thenReturn(Optional.of(EXISTING));
        when(playerRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        PlayerProfile result = service.updateProfile("user-123", new PlayerUpdateRequest("Carol", "Kayaking"));

        assertEquals("Carol", result.displayName());
        assertEquals("Kayaking", result.avatarKey());
    }

    // @spec UM-BE-003
    @Test
    void updateProfile_displayName_isTrimmed() {
        when(playerRepository.findById("user-123")).thenReturn(Optional.of(EXISTING));
        when(playerRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        PlayerProfile result = service.updateProfile("user-123", new PlayerUpdateRequest("  Dave  ", null));

        assertEquals("Dave", result.displayName());
    }

    // @spec UM-BE-004
    @Test
    void updateProfile_profileNotFound_throwsPlayerNotFoundException() {
        when(playerRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThrows(PlayerNotFoundException.class,
                () -> service.updateProfile("unknown", new PlayerUpdateRequest("X", null)));
    }

    // @spec UM-BE-005
    @Test
    void updateProfile_bothFieldsNull_throwsInvalidPlayerUpdateException() {
        assertThrows(InvalidPlayerUpdateException.class,
                () -> service.updateProfile("user-123", new PlayerUpdateRequest(null, null)));
        verifyNoInteractions(playerRepository);
    }

    // @spec UM-BE-006
    @Test
    void updateProfile_blankDisplayName_throwsInvalidPlayerUpdateException() {
        assertThrows(InvalidPlayerUpdateException.class,
                () -> service.updateProfile("user-123", new PlayerUpdateRequest("   ", null)));
        verifyNoInteractions(playerRepository);
    }

    // @spec UM-BE-006
    @Test
    void updateProfile_displayNameExceeds50Chars_throwsInvalidPlayerUpdateException() {
        String tooLong = "A".repeat(51);
        assertThrows(InvalidPlayerUpdateException.class,
                () -> service.updateProfile("user-123", new PlayerUpdateRequest(tooLong, null)));
        verifyNoInteractions(playerRepository);
    }

    // @spec UM-BE-006
    @Test
    void updateProfile_displayNameExactly50Chars_isAccepted() {
        String maxLength = "A".repeat(50);
        when(playerRepository.findById("user-123")).thenReturn(Optional.of(EXISTING));
        when(playerRepository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        PlayerProfile result = service.updateProfile("user-123", new PlayerUpdateRequest(maxLength, null));

        assertEquals(maxLength, result.displayName());
    }
}
