package com.bankmanagement.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Maps a verified JWT to an {@link AuthenticatedUser} and Spring authorities. */
public class JwtUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<String> perms = jwt.getClaimAsStringList(TokenClaims.PERMISSIONS);
        Set<String> permissions = perms == null ? Set.of() : Set.copyOf(perms);
        String role = jwt.getClaimAsString(TokenClaims.ROLE);

        AuthenticatedUser user = new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                uuidOrNull(jwt.getClaimAsString(TokenClaims.EMPLOYEE_ID)),
                jwt.getClaimAsString(TokenClaims.USERNAME),
                jwt.getClaimAsString(TokenClaims.NAME),
                jwt.getClaimAsString(TokenClaims.EMPLOYEE_CODE),
                jwt.getClaimAsString(TokenClaims.TENANT),
                role,
                uuidOrNull(jwt.getClaimAsString(TokenClaims.BRANCH_ID)),
                uuidOrNull(jwt.getClaimAsString(TokenClaims.IMPERSONATOR)),
                permissions);

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        new LinkedHashSet<>(permissions).forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        return new UserAuthenticationToken(user, jwt, authorities);
    }

    private static UUID uuidOrNull(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
