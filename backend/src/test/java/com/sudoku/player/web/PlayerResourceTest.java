package com.sudoku.player.web;

import com.sudoku.auth.UserIdentityResolver;
import com.sudoku.player.PlayerService;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

// @spec UM-BE-001, UM-BE-002, UM-API-001, UM-API-002, UM-DATA-001, UM-DATA-002, UM-DATA-003
class PlayerResourceTest {

    private static final String USER_ID = "user-sub-123";

    // A Principal that also implements JsonWebToken so we can test the cast path.
    interface JwtPrincipal extends Principal, JsonWebToken {}

    private PlayerResource resource;
    private SecurityIdentity identity;
    private PlayerService playerService;
    private UserIdentityResolver userIdentityResolver;

    @BeforeEach
    void setUp() throws Exception {
        resource = new PlayerResource();
        identity = mock(SecurityIdentity.class);
        playerService = mock(PlayerService.class);
        userIdentityResolver = mock(UserIdentityResolver.class);
        when(userIdentityResolver.resolveUserId()).thenReturn(USER_ID);

        inject("identity", identity);
        inject("playerService", playerService);
        inject("userIdentityResolver", userIdentityResolver);
    }

    private void inject(String field, Object value) throws Exception {
        Field f = PlayerResource.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(resource, value);
    }

    /** Stub a JWT principal returning the given email and name claims. */
    private JwtPrincipal stubJwtPrincipal(String email, String name) {
        JwtPrincipal jwt = mock(JwtPrincipal.class);
        when(jwt.getClaim("email")).thenReturn(email);
        when(jwt.getClaim("name")).thenReturn(name);
        when(jwt.getName()).thenReturn(USER_ID);
        when(identity.getPrincipal()).thenReturn(jwt);
        return jwt;
    }

    @Test
    void getMe_extractsEmailAndDisplayName_fromJwtClaims() {
        stubJwtPrincipal("user@example.com", "Alice Smith");
        when(playerService.getOrCreateProfile(any(), any(), any()))
                .thenReturn(new PlayerProfile(USER_ID, "user@example.com", "Alice Smith", null, "now", "now", Boolean.TRUE, 0L, null));

        resource.getMe();

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(playerService).getOrCreateProfile(any(), emailCaptor.capture(), nameCaptor.capture());
        assertEquals("user@example.com", emailCaptor.getValue());
        assertEquals("Alice Smith", nameCaptor.getValue());
    }

    @Test
    void getMe_fallsBackToGetAttribute_whenPrincipalIsNotJwt() {
        // Principal does not implement JsonWebToken
        Principal plainPrincipal = mock(Principal.class);
        when(identity.getPrincipal()).thenReturn(plainPrincipal);
        when(identity.getAttribute("email")).thenReturn("fallback@example.com");
        when(identity.getAttribute("name")).thenReturn("Bob Jones");
        when(playerService.getOrCreateProfile(any(), any(), any()))
                .thenReturn(new PlayerProfile(USER_ID, "fallback@example.com", "Bob Jones", null, "now", "now", Boolean.TRUE, 0L, null));

        resource.getMe();

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(playerService).getOrCreateProfile(any(), emailCaptor.capture(), nameCaptor.capture());
        assertEquals("fallback@example.com", emailCaptor.getValue());
        assertEquals("Bob Jones", nameCaptor.getValue());
    }

    @Test
    void getMe_passesNulls_whenClaimsAbsent() {
        stubJwtPrincipal(null, null);
        when(identity.getAttribute("email")).thenReturn(null);
        when(identity.getAttribute("name")).thenReturn(null);
        when(playerService.getOrCreateProfile(any(), any(), any()))
                .thenReturn(new PlayerProfile(USER_ID, "", "", null, "now", "now", Boolean.TRUE, 0L, null));

        resource.getMe();

        verify(playerService).getOrCreateProfile(any(), isNull(), isNull());
    }

    @Test
    void getMe_prefersJwtClaim_overGetAttribute() {
        // Both sources available — JWT claim should win
        stubJwtPrincipal("jwt@example.com", "JWT Name");
        when(identity.getAttribute("email")).thenReturn("attr@example.com");
        when(identity.getAttribute("name")).thenReturn("Attr Name");
        when(playerService.getOrCreateProfile(any(), any(), any()))
                .thenReturn(new PlayerProfile(USER_ID, "jwt@example.com", "JWT Name", null, "now", "now", Boolean.TRUE, 0L, null));

        resource.getMe();

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(playerService).getOrCreateProfile(any(), emailCaptor.capture(), any());
        assertEquals("jwt@example.com", emailCaptor.getValue());
    }

    // @spec UM-API-002, UM-BE-003
    @Test
    void updateMe_delegatesToService_andReturnsUpdatedProfile() {
        PlayerUpdateRequest request = new PlayerUpdateRequest("New Name", "Surfing", null);
        PlayerProfile expected = new PlayerProfile(USER_ID, "u@e.com", "New Name", "Surfing", "t1", "t2", Boolean.TRUE, 0L, null);
        when(playerService.updateProfile(USER_ID, request)).thenReturn(expected);

        PlayerProfile result = resource.updateMe(request);

        assertEquals(expected, result);
        verify(playerService).updateProfile(USER_ID, request);
    }

    // @spec UM-API-002, UM-DATA-003
    @Test
    void getMe_newProfile_hasNullAvatarKey() {
        stubJwtPrincipal("user@example.com", "Alice");
        PlayerProfile freshProfile = new PlayerProfile(USER_ID, "user@example.com", "Alice", null, "now", "now", Boolean.TRUE, 0L, null);
        when(playerService.getOrCreateProfile(any(), any(), any())).thenReturn(freshProfile);

        PlayerProfile result = resource.getMe();

        assertNull(result.avatarKey());
    }
}
