package com.sudoku.web.filter;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

// @spec UM-BE-010, UM-BE-011, UM-BE-012, UM-BE-020, UM-BE-021, UM-BE-022, UM-GCP-005
class AllowedUsersFilterTest {

    // A Principal that also implements JsonWebToken so we can test the cast path.
    interface JwtPrincipal extends Principal, JsonWebToken {}

    private AllowedUsersFilter filter;
    private SecurityIdentity identity;
    private ContainerRequestContext ctx;

    @BeforeEach
    void setUp() {
        identity = mock(SecurityIdentity.class);
        ctx = mock(ContainerRequestContext.class);
    }

    private void setAllowedEmails(String value) throws Exception {
        filter = new AllowedUsersFilter(Optional.ofNullable(value));
        Field identityField = AllowedUsersFilter.class.getDeclaredField("identity");
        identityField.setAccessible(true);
        identityField.set(filter, identity);
    }

    /** Stub identity with a JWT principal returning the given (verified) email from getClaim. */
    private void stubJwtEmail(String email) {
        stubJwtEmail(email, true);
    }

    /** Stub identity with a JWT principal returning the given email and email_verified flag. */
    private void stubJwtEmail(String email, boolean emailVerified) {
        JwtPrincipal jwt = mock(JwtPrincipal.class);
        when(jwt.getClaim("email")).thenReturn(email);
        when(jwt.getClaim("email_verified")).thenReturn(emailVerified);
        when(identity.getPrincipal()).thenReturn(jwt);
    }

    /** Stub a Firebase (GCP) JWT — carries a {@code firebase} claim — with the given verified flag. */
    private void stubFirebaseJwtEmail(String email, boolean emailVerified) {
        JwtPrincipal jwt = mock(JwtPrincipal.class);
        when(jwt.getClaim("email")).thenReturn(email);
        when(jwt.getClaim("email_verified")).thenReturn(emailVerified);
        when(jwt.getClaim("firebase")).thenReturn(java.util.Map.of("sign_in_provider", "google.com"));
        when(identity.getPrincipal()).thenReturn(jwt);
    }

    @Test
    void allowedEmail_passesThrough() throws Exception {
        setAllowedEmails("edoatley@gmail.com,hanoatley@gmail.com");
        when(identity.isAnonymous()).thenReturn(false);
        stubJwtEmail("edoatley@gmail.com");

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void allowedEmail_viaAttributeFallback_passesThrough() throws Exception {
        setAllowedEmails("edoatley@gmail.com,hanoatley@gmail.com");
        when(identity.isAnonymous()).thenReturn(false);
        // Principal is not a JsonWebToken — falls back to getAttribute
        when(identity.getPrincipal()).thenReturn(mock(Principal.class));
        when(identity.getAttribute("email")).thenReturn("edoatley@gmail.com");
        when(identity.getAttribute("email_verified")).thenReturn(true);

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    // @spec UM-BE-020: a Cognito (AWS) token has no `firebase` claim; Cognito sets email_verified
    // =false for Google-federated users, but the allowlisted email is still trusted, so it passes.
    @Test
    void cognitoUnverifiedButAllowlisted_passesThrough() throws Exception {
        setAllowedEmails("edoatley@gmail.com,hanoatley@gmail.com");
        when(identity.isAnonymous()).thenReturn(false);
        stubJwtEmail("edoatley@gmail.com", false);

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    // @spec UM-GCP-005: a Firebase (GCP) token with email_verified=false is rejected even if allowlisted.
    @Test
    void firebaseUnverifiedEmail_returns403() throws Exception {
        setAllowedEmails("edoatley@gmail.com,hanoatley@gmail.com");
        when(identity.isAnonymous()).thenReturn(false);
        stubFirebaseJwtEmail("edoatley@gmail.com", false);

        filter.filter(ctx);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(403, captor.getValue().getStatus());
    }

    // @spec UM-GCP-005: a Firebase token with email_verified=true and an allowlisted email passes.
    @Test
    void firebaseVerifiedEmail_passesThrough() throws Exception {
        setAllowedEmails("edoatley@gmail.com,hanoatley@gmail.com");
        when(identity.isAnonymous()).thenReturn(false);
        stubFirebaseJwtEmail("edoatley@gmail.com", true);

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void disallowedEmail_returns403() throws Exception {
        setAllowedEmails("edoatley@gmail.com,hanoatley@gmail.com");
        when(identity.isAnonymous()).thenReturn(false);
        stubJwtEmail("stranger@example.com");

        filter.filter(ctx);

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(403, captor.getValue().getStatus());
    }

    @Test
    void missingEmailClaim_returns403() throws Exception {
        setAllowedEmails("edoatley@gmail.com");
        when(identity.isAnonymous()).thenReturn(false);
        stubJwtEmail(null);
        when(identity.getAttribute("email")).thenReturn(null);

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
        stubJwtEmail("hanoatley@gmail.com");

        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }
}
