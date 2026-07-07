package com.sudoku.player.web;

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

    // A Principal that also implements JsonWebToken so we can test the cast path.
    interface JwtPrincipal extends Principal, JsonWebToken {}

    private PlayerResource resource;
    private SecurityIdentity identity;
    private PlayerService playerService;

    @BeforeEach
    void setUp() throws Exception {
        resource = new PlayerResource();
        identity = mock(SecurityIdentity.class);
        playerService = mock(PlayerService.class);

        Field identityField = PlayerResource.class.getDeclaredField("identity");
        identityField.setAccessible(true);
        identityField.set(resource, identity);

        Field serviceField = PlayerResource.class.getDeclaredField("playerService");
        serviceField.setAccessible(true);
        serviceField.set(resource, playerService);
    }

    /** Stub a JWT principal returning the given email and name claims. */
    private JwtPrincipal stubJwtPrincipal(String email, String name) {
        JwtPrincipal jwt = mock(JwtPrincipal.class);
        when(jwt.getClaim("email")).thenReturn(email);
        when(jwt.getClaim("name")).thenReturn(name);
        when(jwt.getName()).thenReturn("user-sub-123");
        when(identity.getPrincipal()).thenReturn(jwt);
        return jwt;
    }

    @Test
    void getMe_extractsEmailAndDisplayName_fromJwtClaims() throws Exception {
        stubJwtPrincipal("user@example.com", "Alice Smith");
        when(playerService.getOrCreateProfile(any(), any(), any()))
                .thenReturn(new PlayerProfile("user-sub-123", "user@example.com", "Alice Smith", null, "now", "now", Boolean.TRUE, 0L, null));

        var sc = mock(jakarta.ws.rs.core.SecurityContext.class);
        when(sc.getUserPrincipal()).thenReturn(mock(Principal.class));
        when(sc.getUserPrincipal().getName()).thenReturn("user-sub-123");

        resource.getMe(sc);

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(playerService).getOrCreateProfile(any(), emailCaptor.capture(), nameCaptor.capture());
        assertEquals("user@example.com", emailCaptor.getValue());
        assertEquals("Alice Smith", nameCaptor.getValue());
    }

    @Test
    void getMe_fallsBackToGetAttribute_whenPrincipalIsNotJwt() throws Exception {
        // Principal does not implement JsonWebToken
        Principal plainPrincipal = mock(Principal.class);
        when(plainPrincipal.getName()).thenReturn("user-sub-456");
        when(identity.getPrincipal()).thenReturn(plainPrincipal);
        when(identity.getAttribute("email")).thenReturn("fallback@example.com");
        when(identity.getAttribute("name")).thenReturn("Bob Jones");
        when(playerService.getOrCreateProfile(any(), any(), any()))
                .thenReturn(new PlayerProfile("user-sub-456", "fallback@example.com", "Bob Jones", null, "now", "now", Boolean.TRUE, 0L, null));

        var sc = mock(jakarta.ws.rs.core.SecurityContext.class);
        when(sc.getUserPrincipal()).thenReturn(plainPrincipal);

        resource.getMe(sc);

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(playerService).getOrCreateProfile(any(), emailCaptor.capture(), nameCaptor.capture());
        assertEquals("fallback@example.com", emailCaptor.getValue());
        assertEquals("Bob Jones", nameCaptor.getValue());
    }

    @Test
    void getMe_passesNulls_whenClaimsAbsent() throws Exception {
        stubJwtPrincipal(null, null);
        when(identity.getAttribute("email")).thenReturn(null);
        when(identity.getAttribute("name")).thenReturn(null);
        when(playerService.getOrCreateProfile(any(), any(), any()))
                .thenReturn(new PlayerProfile("user-sub-123", "", "", null, "now", "now", Boolean.TRUE, 0L, null));

        var sc = mock(jakarta.ws.rs.core.SecurityContext.class);
        when(sc.getUserPrincipal()).thenReturn(mock(Principal.class));
        when(sc.getUserPrincipal().getName()).thenReturn("user-sub-123");

        resource.getMe(sc);

        verify(playerService).getOrCreateProfile(any(), isNull(), isNull());
    }

    @Test
    void getMe_prefersJwtClaim_overGetAttribute() throws Exception {
        // Both sources available — JWT claim should win
        JwtPrincipal jwt = stubJwtPrincipal("jwt@example.com", "JWT Name");
        when(identity.getAttribute("email")).thenReturn("attr@example.com");
        when(identity.getAttribute("name")).thenReturn("Attr Name");
        when(playerService.getOrCreateProfile(any(), any(), any()))
                .thenReturn(new PlayerProfile("user-sub-123", "jwt@example.com", "JWT Name", null, "now", "now", Boolean.TRUE, 0L, null));

        var sc = mock(jakarta.ws.rs.core.SecurityContext.class);
        when(sc.getUserPrincipal()).thenReturn(jwt);

        resource.getMe(sc);

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(playerService).getOrCreateProfile(any(), emailCaptor.capture(), any());
        assertEquals("jwt@example.com", emailCaptor.getValue());
    }

    // @spec UM-API-002, UM-BE-003
    @Test
    void updateMe_delegatesToService_andReturnsUpdatedProfile() throws Exception {
        var sc = mock(jakarta.ws.rs.core.SecurityContext.class);
        var principal = mock(java.security.Principal.class);
        when(sc.getUserPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("user-sub-123");

        PlayerUpdateRequest request = new PlayerUpdateRequest("New Name", "Surfing", null);
        PlayerProfile expected = new PlayerProfile("user-sub-123", "u@e.com", "New Name", "Surfing", "t1", "t2", Boolean.TRUE, 0L, null);
        when(playerService.updateProfile("user-sub-123", request)).thenReturn(expected);

        PlayerProfile result = resource.updateMe(sc, request);

        assertEquals(expected, result);
        verify(playerService).updateProfile("user-sub-123", request);
    }

    // @spec UM-API-002, UM-DATA-003
    @Test
    void getMe_newProfile_hasNullAvatarKey() throws Exception {
        stubJwtPrincipal("user@example.com", "Alice");
        PlayerProfile freshProfile = new PlayerProfile("user-sub-123", "user@example.com", "Alice", null, "now", "now", Boolean.TRUE, 0L, null);
        when(playerService.getOrCreateProfile(any(), any(), any())).thenReturn(freshProfile);

        var sc = mock(jakarta.ws.rs.core.SecurityContext.class);
        when(sc.getUserPrincipal()).thenReturn(mock(java.security.Principal.class));

        PlayerProfile result = resource.getMe(sc);

        assertNull(result.avatarKey());
    }
}
