package com.bankmanagement.auth.config;

import com.bankmanagement.common.security.TokenClaims;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Collection;

/**
 * RS256 signing keys. Only the auth service holds the private key; every other service verifies
 * tokens through the public JWKS endpoint.
 */
@Slf4j
@Configuration
public class JwtKeyConfig {

    @Bean
    public RSAKey signingKey(AuthProperties props) throws Exception {
        String pem = props.jwt().privateKey();
        RSAPrivateCrtKey privateKey;
        RSAPublicKey publicKey;
        if (pem == null || pem.isBlank()) {
            if (!props.jwt().allowEphemeralKey()) {
                throw new IllegalStateException("app.auth.jwt.private-key (JWT_PRIVATE_KEY) must be configured.");
            }
            log.warn("No JWT signing key configured - generating an EPHEMERAL key. Tokens die on restart. DEV ONLY.");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            privateKey = (RSAPrivateCrtKey) pair.getPrivate();
            publicKey = (RSAPublicKey) pair.getPublic();
        } else {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            privateKey = (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(pem)));
            publicKey = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        }
        RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).keyUse(KeyUse.SIGNATURE).build();
        return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey signingKey) {
        return new ImmutableJWKSet<>(new JWKSet(signingKey));
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /** This service also accepts its own tokens (change password, admin endpoints). */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey signingKey, AuthProperties props) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(TokenClaims.AUDIENCE));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.jwt().issuer()), audience));
        return decoder;
    }

    private static byte[] decodePem(String value) {
        String pem = value.contains("BEGIN") ? value
                : new String(Base64.getDecoder().decode(value.trim()), StandardCharsets.UTF_8);
        String body = pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replace("\\n", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
