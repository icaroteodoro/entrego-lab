package br.com.icaroteodoro.entrego.auth.security;

import org.springframework.core.io.Resource;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        Resource privateKey,
        Resource publicKey,
        String issuer,
        Duration accessTokenExpiration,
        Duration refreshTokenExpiration
) {
}
