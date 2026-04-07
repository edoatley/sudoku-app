package com.sudoku.auth;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AllowedUsersFilterTest {

    private AllowedUsersFilter filter;
    private SecurityIdentity identity;
    private JsonWebToken jwt;
    private ContainerRequestContext ctx;

    @BeforeEach
    void setUp() throws Exception {
        filter = new AllowedUsersFilter();
        identity = mock(SecurityIdentity.class);
        jwt = mock(JsonWebToken.class);
        ctx = mock(ContainerRequestContext.class);

        Field identityField = AllowedUsersFilter.class.getDeclaredField("identity");
        identityField.setAccessible(true);
        identityField.set(filter, identity);

        Field jwtField = AllowedUsersFilter.class.getDeclaredField("jwt");
        jwtField.setAccessible(true);
        jwtField.set(filter, jwt);
    }

    private void setAllowedEmails(String value) throws Exception {
        Field field = AllowedUsersFilter.class.getDeclaredField("allowedEmailsRaw");
        field.setAccessible(true);
        field.set(filter, Optional.ofNullable(value));
    }

    @Test
    void allowedEmail_passesThrough() throws Exception {
        setAllowedEmails("edoatley@gmail.com,hanoatley@gmail.com");
        when(identity.isAnonymous()).thenReturn(false);
        when(jwt.getClaim("email")).thenReturn("edoatley@gmail.com");

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void disallowedEmail_returns403() throws Exception {
        setAllowedEmails("edoatley@gmail.com,hanoatley@gmail.com");
        when(identity.isAnonymous()).thenReturn(false);
        when(jwt.getClaim("email")).thenReturn("stranger@example.com");

        filter.filter(ctx);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(403, captor.getValue().getStatus());
    }

    @Test
    void missingEmailClaim_returns403() throws Exception {
        setAllowedEmails("edoatley@gmail.com");
        when(identity.isAnonymous()).thenReturn(false);
        when(jwt.getClaim("email")).thenReturn(null);

        filter.filter(ctx);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(403, captor.getValue().getStatus());
    }

    @Test
    void emptyAllowlist_disablesCheck() throws Exception {
        setAllowedEmails("");
        filter.filter(ctx);
        verify(ctx, never()).abortWith(any());
        verifyNoInteractions(identity);
    }

    @Test
    void nullAllowlist_disablesCheck() throws Exception {
        setAllowedEmails(null);
        filter.filter(ctx);
        verify(ctx, never()).abortWith(any());
        verifyNoInteractions(identity);
    }

    @Test
    void anonymousIdentity_skipsCheck() throws Exception {
        setAllowedEmails("edoatley@gmail.com");
        when(identity.isAnonymous()).thenReturn(true);

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void whitespaceAroundEmails_isTrimmed() throws Exception {
        setAllowedEmails("  edoatley@gmail.com , hanoatley@gmail.com  ");
        when(identity.isAnonymous()).thenReturn(false);
        when(jwt.getClaim("email")).thenReturn("hanoatley@gmail.com");

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }
}
