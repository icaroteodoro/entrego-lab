package br.com.icaroteodoro.entrego.auth.auth;

import br.com.icaroteodoro.entrego.auth.auth.dtos.*;
import br.com.icaroteodoro.entrego.auth.security.JwtProperties;
import br.com.icaroteodoro.entrego.auth.security.JwtService;
import br.com.icaroteodoro.entrego.auth.token.RefreshToken;
import br.com.icaroteodoro.entrego.auth.token.RefreshTokenService;
import br.com.icaroteodoro.entrego.auth.user.User;
import br.com.icaroteodoro.entrego.auth.user.UserRepository;
import br.com.icaroteodoro.entrego.auth.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;

    public LoginResponseDTO login(LoginRequestDTO request) {

        User user = userRepository
                .findByEmailIgnoreCase(request.email())
                .orElseThrow(() ->
                        new BadCredentialsException(
                                "Invalid email or password"
                        )
                );

        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new BadCredentialsException(
                    "Invalid email or password"
            );
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new DisabledException(
                    "User account is not active"
            );
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.create(user);

        return new LoginResponseDTO(
                accessToken,
                refreshToken,
                jwtProperties.accessTokenExpiration().toSeconds(),
                "Bearer"
        );
    }

    @Transactional
    public LoginResponseDTO refresh(RefreshTokenRequestDTO request) {
        RefreshToken currentRefreshToken =
                refreshTokenService.validate(request.refreshToken());

        User user = currentRefreshToken.getUser();

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new DisabledException("User account is not active");
        }

        currentRefreshToken.revoke();

        String accessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = refreshTokenService.create(user);

        return new LoginResponseDTO(
                accessToken,
                newRefreshToken,
                jwtProperties.accessTokenExpiration().toSeconds(),
                "Bearer"
        );
    }

    @Transactional
    public void logout(LogoutRequestDTO request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    @Transactional(readOnly = true)
    public MeResponseDTO me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found")
                );

        List<String> roles = user.getRoles()
                .stream()
                .map(role -> role.getName().name())
                .toList();

        return new MeResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                roles
        );
    }
}
