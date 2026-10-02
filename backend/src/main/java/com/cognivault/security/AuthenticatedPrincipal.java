package com.cognivault.security;

import java.security.Principal;
import java.util.UUID;

public record AuthenticatedPrincipal(UUID userId, String username)
        implements CurrentActor.AuthenticatedPrincipal, Principal {

    @Override
    public String getName() {
        return username;
    }
}
