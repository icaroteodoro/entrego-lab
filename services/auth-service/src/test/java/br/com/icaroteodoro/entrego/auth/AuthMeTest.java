package br.com.icaroteodoro.entrego.auth;

import br.com.icaroteodoro.entrego.auth.role.RoleName;
import br.com.icaroteodoro.entrego.auth.user.User;
import br.com.icaroteodoro.entrego.auth.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("7. Testes do Endpoint /auth/me")
class AuthMeTest extends BaseIntegrationTest {

    @Test
    @DisplayName("JWT válido → retorna usuário correto com id, name, email e roles")
    void shouldReturnAuthenticatedUserWithAllPublicFieldsWhenJwtIsValid() throws Exception {
        User user = createTestUser("Juliana Mendes", "juliana@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.name").value("Juliana Mendes"))
                .andExpect(jsonPath("$.email").value("juliana@example.com"))
                .andExpect(jsonPath("$.roles", hasItem("CUSTOMER")));
    }

    @Test
    @DisplayName("sub do JWT é utilizado para localizar o usuário correto")
    void shouldLocateUserSpecificallyBySubClaimInJwt() throws Exception {
        User user1 = createTestUser("Usuario Um", "um@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);
        User user2 = createTestUser("Usuario Dois", "dois@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        String tokenUser2 = jwtService.generateAccessToken(user2);

        // Deve retornar dados do user2, nunca do user1
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user2.getId().toString()))
                .andExpect(jsonPath("$.email").value("dois@example.com"))
                .andExpect(jsonPath("$.name").value("Usuario Dois"));
    }

    @Test
    @DisplayName("Sem JWT → 401")
    void shouldReturn401WhenRequestHasNoJwtHeader() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT inválido → 401")
    void shouldReturn401WhenJwtIsInvalid() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer token_completamente_malformado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Usuário inexistente no banco mas com token válido → resposta apropriada")
    void shouldReturnAppropriateErrorWhenUserDoesNotExistInDatabase() throws Exception {
        UUID nonExistentUserId = UUID.randomUUID();

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("entrego-auth")
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .subject(nonExistentUserId.toString())
                .claim("roles", List.of("CUSTOMER"))
                .build();

        String ghostToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        try {
            MvcResult result = mockMvc.perform(get("/auth/me")
                            .header("Authorization", "Bearer " + ghostToken))
                    .andReturn();

            assertTrue(result.getResponse().getStatus() >= 400, "Deve retornar status de erro quando usuário não existe");
        } catch (Exception ex) {
            assertTrue(ex instanceof IllegalArgumentException || (ex.getCause() != null && ex.getCause() instanceof IllegalArgumentException),
                    "Deve disparar IllegalArgumentException('User not found')");
        }
    }

    @Test
    @DisplayName("Dados sensíveis, principalmente password e tokenHash, nunca aparecem na resposta")
    void shouldNeverExposeSensitiveDataInMeResponse() throws Exception {
        User user = createTestUser("User Privacy", "privacy@example.com", "senhaUltraSecreta123@", RoleName.CUSTOMER, UserStatus.ACTIVE);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andExpect(jsonPath("$.tokens").doesNotExist());
    }
}
