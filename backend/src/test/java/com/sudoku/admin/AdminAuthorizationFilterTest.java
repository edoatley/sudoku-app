package com.sudoku.admin;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

// @spec UM-BE-060, UM-BE-061, UM-BE-062
class AdminAuthorizationFilterTest {

    // A Principal that also implements JsonWebToken so we can test the cast path.
    interface JwtPrincipal extends Principal, JsonWebToken {}

    private AdminAuthorizationFilter filter;
    private SecurityIdentity identity;
    private ContainerRequestContext ctx;

    @BeforeEach
    void setUp() throws Exception {
        filter = new AdminAuthorizationFilter();
        identity = mock(SecurityIdentity.class);
        ctx = mock(ContainerRequestContext.class);
        setField("identity", identity);
        setField("adminGroup", "administrators");
    }

    private void setField(String name, Object value) throws Exception {
        Field field = AdminAuthorizationFilter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(filter, value);
    }

    /** Stub identity with a JWT principal returning the given cognito:groups claim. */
    private void stubJwtGroups(Object groupsClaim) {
        JwtPrincipal jwt = mock(JwtPrincipal.class);
        when(jwt.getClaim("cognito:groups")).thenReturn(groupsClaim);
        when(identity.getPrincipal()).thenReturn(jwt);
    }

    private JsonArray jsonArrayOf(String... values) {
        var builder = Json.createArrayBuilder();
        for (String value : values) builder.add(value);
        return builder.build();
    }

    @Test
    void adminGroupMember_viaJsonArrayClaim_passesThrough() {
        when(identity.isAnonymous()).thenReturn(false);
        stubJwtGroups(jsonArrayOf("administrators", "other-group"));

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void adminGroupMember_viaCollectionClaim_passesThrough() {
        when(identity.isAnonymous()).thenReturn(false);
        stubJwtGroups(List.of("administrators"));

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void adminGroupMember_viaAttributeFallback_passesThrough() {
        when(identity.isAnonymous()).thenReturn(false);
        // Principal is not a JsonWebToken — falls back to getAttribute
        when(identity.getPrincipal()).thenReturn(mock(Principal.class));
        when(identity.getAttribute("cognito:groups")).thenReturn(List.of("administrators"));

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void authenticatedNonMember_returns403() {
        when(identity.isAnonymous()).thenReturn(false);
        stubJwtGroups(jsonArrayOf("some-other-group"));

        filter.filter(ctx);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(403, captor.getValue().getStatus());
    }

    @Test
    void emptyGroupsClaim_returns403() {
        when(identity.isAnonymous()).thenReturn(false);
        stubJwtGroups(jsonArrayOf());

        filter.filter(ctx);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(403, captor.getValue().getStatus());
    }

    @Test
    void missingGroupsClaim_returns403() {
        when(identity.isAnonymous()).thenReturn(false);
        stubJwtGroups(null);
        when(identity.getAttribute("cognito:groups")).thenReturn(null);

        filter.filter(ctx);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(403, captor.getValue().getStatus());
    }

    @Test
    void anonymousIdentity_skipsCheck() {
        when(identity.isAnonymous()).thenReturn(true);

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
        verify(identity, never()).getPrincipal();
    }
}
