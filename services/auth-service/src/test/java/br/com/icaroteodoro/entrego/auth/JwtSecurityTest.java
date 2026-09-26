package br.com.icaroteodoro.entrego.auth;

import br.com.icaroteodoro.entrego.auth.role.RoleName;
import br.com.icaroteodoro.entrego.auth.user.User;
import br.com.icaroteodoro.entrego.auth.user.UserStatus;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("6. Testes de Segurança Criptográfica do JWT")
class JwtSecurityTest extends BaseIntegrationTest {

    @Test
    @DisplayName("JWT assinado pela private key correta → aceito")
    void shouldAcceptJwtSignedWithCorrectPrivateKey() throws Exception {
        User user = createTestUser("User Crypto", "crypto@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);
        String validToken = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("JWT adulterado → 401")
    void shouldReturn401WhenJwtPayloadIsTampered() throws Exception {
        User user = createTestUser("User Tampered", "tampered@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);
        String validToken = jwtService.generateAccessToken(user);

        String[] parts = validToken.split("\\.");
        // Decodifica payload, altera caractere e remonta token sem assinar
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tamperedPayload = payloadJson.replace("entrego-auth", "hacked-issuer");
        String tamperedBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8));
        String tamperedToken = parts[0] + "." + tamperedBase64 + "." + parts[2];

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT expirado → 401")
    void shouldReturn401WhenJwtIsExpired() throws Exception {
        User user = createTestUser("User Expired", "expiredjwt@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        Instant now = Instant.now();
        JwtClaimsSet expiredClaims = JwtClaimsSet.builder()
                .issuer("entrego-auth")
                .issuedAt(now.minus(30, ChronoUnit.MINUTES))
                .expiresAt(now.minus(15, ChronoUnit.MINUTES))
                .subject(user.getId().toString())
                .claim("roles", List.of("CUSTOMER"))
                .build();

        String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(expiredClaims)).getTokenValue();

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT com assinatura inválida → 401")
    void shouldReturn401WhenJwtSignatureIsCorrupted() throws Exception {
        User user = createTestUser("User CorruptedSig", "corruptedsig@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);
        String validToken = jwtService.generateAccessToken(user);

        String[] parts = validToken.split("\\.");
        // Modifica bytes significativos da assinatura
        String corruptedSignature = "invalid_signature_" + parts[2].substring(18);
        String corruptedToken = parts[0] + "." + parts[1] + "." + corruptedSignature;

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + corruptedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT assinado com outra chave privada → 401")
    void shouldReturn401WhenJwtSignedWithForeignPrivateKey() throws Exception {
        User user = createTestUser("User ForeignKey", "foreignkey@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        // Gera par de chaves RSA efêmero desconhecido pelo Resource Server
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair foreignKp = kpg.generateKeyPair();

        JWK foreignJwk = new RSAKey.Builder((RSAPublicKey) foreignKp.getPublic())
                .privateKey((RSAPrivateKey) foreignKp.getPrivate())
                .build();
        JWKSource<SecurityContext> foreignJwks = new ImmutableJWKSet<>(new JWKSet(foreignJwk));
        JwtEncoder foreignEncoder = new NimbusJwtEncoder(foreignJwks);

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("entrego-auth")
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .subject(user.getId().toString())
                .claim("roles", List.of("CUSTOMER"))
                .build();

        String foreignToken = foreignEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sub corresponde ao UUID do usuário")
    void shouldEnsureSubCorrespondsToUserUuid() {
        User user = createTestUser("User SubCheck", "subcheck@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);
        String token = jwtService.generateAccessToken(user);

        Jwt jwt = jwtDecoder.decode(token);
        assertEquals(user.getId().toString(), jwt.getSubject(), "O claim 'sub' deve corresponder ao UUID do usuário");
        assertEquals(user.getId(), UUID.fromString(jwt.getSubject()), "O claim 'sub' deve ser um UUID válido");
    }

    @Test
    @DisplayName("Roles não podem ser alteradas manualmente no payload sem invalidar a assinatura")
    void shouldDenyPrivilegeEscalationWhenRolesAreTamperedInPayload() throws Exception {
        User customer = createTestUser("User Normal", "normal@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);
        String customerToken = jwtService.generateAccessToken(customer);

        // Altera 'CUSTOMER' para 'ADMIN' no payload Base64 mantendo a assinatura original
        String[] parts = customerToken.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tamperedPayloadJson = payloadJson.replace("CUSTOMER", "ADMIN");
        String tamperedPayloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayloadJson.getBytes(StandardCharsets.UTF_8));
        String privilegeEscalationToken = parts[0] + "." + tamperedPayloadBase64 + "." + parts[2];

        // A tentativa de acessar o endpoint administrativo falha na validação da assinatura
        mockMvc.perform(get("/auth/admin-role-test")
                        .header("Authorization", "Bearer " + privilegeEscalationToken))
                .andExpect(status().isUnauthorized());
    }
}
