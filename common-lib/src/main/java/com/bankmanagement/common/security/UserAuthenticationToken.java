package com.bankmanagement.common.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

/** Authentication carrying the resolved {@link AuthenticatedUser} as principal. */
public class UserAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedUser principal;
    private final transient Jwt jwt;

    public UserAuthenticationToken(AuthenticatedUser principal, Jwt jwt,
                                   Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }

    @Override
    public Jwt getCredentials() {
        return jwt;
    }

    @Override
    public String getName() {
        return principal.userId().toString();
    }
}
