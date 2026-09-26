package br.com.icaroteodoro.entrego.auth.token;

import br.com.icaroteodoro.entrego.auth.exceptions.InvalidRefreshTokenException;
import br.com.icaroteodoro.entrego.auth.security.JwtProperties;
import br.com.icaroteodoro.entrego.auth.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String create(User user) {
        String rawToken = generateToken();
        String tokenHash = hash(rawToken);

        LocalDateTime expiresAt = LocalDateTime.now()
                .plus(jwtProperties.refreshTokenExpiration());

        RefreshToken refreshToken = new RefreshToken(
                user,
                tokenHash,
                expiresAt
        );

        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return Base64.getEncoder()
                    .encodeToString(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm not available",
                    e
            );
        }
    }

    @Transactional(readOnly = true)
    public RefreshToken validate(String rawToken) {
        String tokenHash = hash(rawToken);

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() ->
                        new InvalidRefreshTokenException("Invalid refresh token")
                );

        if (!refreshToken.isValid()) {
            throw new InvalidRefreshTokenException("Invalid or expired refresh token");
        }

        return refreshToken;
    }

    @Transactional
    public void revoke(String rawToken) {
        String tokenHash = hash(rawToken);

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() ->
                        new InvalidRefreshTokenException(
                                "Invalid refresh token"
                        )
                );

        if (refreshToken.isRevoked()) {
            return;
        }

        refreshToken.revoke();
    }


}