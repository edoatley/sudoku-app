package com.sudoku.developer;

import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Map;
import java.util.Set;

/**
 * Minimal {@link JsonWebToken} backed by a fixed claim map, used only by {@link DevIdentityAugmentor}
 * to present a Firebase-shaped identity in dev/it/test. The three abstract methods are enough —
 * {@code getSubject()}, {@code getGroups()}, etc. are default methods deriving from {@link #getClaim}.
 */
final class MockJsonWebToken implements JsonWebToken {

    private final String name;
    private final Map<String, Object> claims;

    MockJsonWebToken(String name, Map<String, Object> claims) {
        this.name = name;
        this.claims = claims;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<String> getClaimNames() {
        return claims.keySet();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getClaim(String claimName) {
        return (T) claims.get(claimName);
    }
}
