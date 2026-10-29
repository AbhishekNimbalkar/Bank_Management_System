package com.bankmanagement.auth.service;

import com.bankmanagement.auth.config.AuthProperties;
import com.bankmanagement.auth.domain.RefreshToken;
import com.bankmanagement.auth.domain.UserAccount;
import com.bankmanagement.auth.repository.RefreshTokenRepository;
import com.bankmanagement.common.security.TokenClaims;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final RSAKey signingKey;
    private final AuthProperties props;
    private final RefreshTokenRepository refreshTokens;
    private final SecureRandom random = new SecureRandom();

    public String issueAccessToken(UserAccount user, Set<String> permissions, UUID impersonatorId, Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .subject(user.getId().toString())
                .audience(List.of(TokenClaims.AUDIENCE))
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(ttl))
                .claim(TokenClaims.TENANT, user.getTenantCode())
                .claim(TokenClaims.ROLE, user.getRole())
                .claim(TokenClaims.NAME, user.getDisplayName())
                .claim(TokenClaims.USERNAME, user.getUsername())
                .claim(TokenClaims.PERMISSIONS, List.copyOf(permissions));
        if (user.getEmployeeId() != null) {
            claims.claim(TokenClaims.EMPLOYEE_ID, user.getEmployeeId().toString());
        }
        if (user.getEmployeeCode() != null) {
            claims.claim(TokenClaims.EMPLOYEE_CODE, user.getEmployeeCode());
        }
        if (user.getBranchId() != null) {
            claims.claim(TokenClaims.BRANCH_ID, user.getBranchId().toString());
        }
        if (impersonatorId != null) {
            claims.claim(TokenClaims.IMPERSONATOR, impersonatorId.toString());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    /** Creates a new refresh token and returns its raw value (only the hash is persisted). */
    public String issueRefreshToken(UserAccount user, UUID familyId, String ip, String userAgent) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTenantCode(user.getTenantCode());
        token.setTokenHash(hash(raw));
        token.setFamilyId(familyId);
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(props.jwt().refreshTokenTtl()));
        token.setIpAddress(ip);
        token.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(200, userAgent.length())));
        refreshTokens.save(token);
        return raw;
    }

    public static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
