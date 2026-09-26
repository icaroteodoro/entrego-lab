package br.com.icaroteodoro.entrego.auth.security;

import br.com.icaroteodoro.entrego.auth.role.Role;
import br.com.icaroteodoro.entrego.auth.role.RoleName;
import br.com.icaroteodoro.entrego.auth.user.User;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder encoder;


    public String generateAccessToken(User user) {

        Instant now = Instant.now();

        List<RoleName> roles = user.getRoles()
                .stream()
                .map(Role::getName)
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("entrego-auth")
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .subject(user.getId().toString())
                .claim("roles", roles)
                .build();

        return encoder.encode(
                JwtEncoderParameters.from(claims)
        ).getTokenValue();
    }
}