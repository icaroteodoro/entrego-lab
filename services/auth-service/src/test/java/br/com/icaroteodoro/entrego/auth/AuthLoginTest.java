package br.com.icaroteodoro.entrego.auth;

import br.com.icaroteodoro.entrego.auth.auth.dtos.LoginRequestDTO;
import br.com.icaroteodoro.entrego.auth.auth.dtos.LoginResponseDTO;
import br.com.icaroteodoro.entrego.auth.role.RoleName;
import br.com.icaroteodoro.entrego.auth.token.RefreshToken;
import br.com.icaroteodoro.entrego.auth.user.User;
import br.com.icaroteodoro.entrego.auth.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MvcResult;

import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("2. Testes de Login")
class AuthLoginTest extends BaseIntegrationTest {

    @Test
    @DisplayName("E-mail + senha corretos → retorna access token e refresh token")
    void shouldReturnTokensWhenCredentialsAreCorrect() throws Exception {
        createTestUser("User Login", "login@example.com", "senhaSegura123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        LoginRequestDTO request = new LoginRequestDTO("login@example.com", "senhaSegura123@");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("Senha incorreta → 401")
    void shouldReturn401WhenPasswordIsIncorrect() throws Exception {
        createTestUser("User Login", "login@example.com", "senhaCorreta123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        LoginRequestDTO request = new LoginRequestDTO("login@example.com", "senhaErrada123@");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("E-mail inexistente → 401")
    void shouldReturn401WhenEmailDoesNotExist() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("naoexiste@example.com", "qualquerSenha123@");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Usuário INACTIVE → login bloqueado")
    void shouldBlockLoginWhenUserIsInactive() throws Exception {
        createTestUser("Inativo", "inativo@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.INACTIVE);

        LoginRequestDTO request = new LoginRequestDTO("inativo@example.com", "senha123@");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("User account is not active"));
    }

    @Test
    @DisplayName("Usuário BLOCKED → login bloqueado")
    void shouldBlockLoginWhenUserIsBlocked() throws Exception {
        createTestUser("Bloqueado", "bloqueado@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.BLOCKED);

        LoginRequestDTO request = new LoginRequestDTO("bloqueado@example.com", "senha123@");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("User account is not active"));
    }

    @Test
    @DisplayName("Usuário PENDING_VERIFICATION → login bloqueado")
    void shouldBlockLoginWhenUserIsPendingVerification() throws Exception {
        createTestUser("Pendente", "pendente@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.PENDING_VERIFICATION);

        LoginRequestDTO request = new LoginRequestDTO("pendente@example.com", "senha123@");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("User account is not active"));
    }

    @Test
    @DisplayName("Access token contém sub com UUID correto, roles corretas, iss entrego-auth, iat e exp de ~15min")
    void shouldValidateAllJwtClaimsStructureAndExpiration() throws Exception {
        User user = createTestUser("User Claims", "claims@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        LoginRequestDTO request = new LoginRequestDTO("claims@example.com", "senha123@");

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponseDTO response = objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponseDTO.class);
        Jwt jwt = jwtDecoder.decode(response.accessToken());

        // sub com UUID correto
        assertEquals(user.getId().toString(), jwt.getSubject(), "O claim 'sub' deve ser exatamente o UUID do usuário");

        // roles corretas
        List<String> roles = jwt.getClaimAsStringList("roles");
        assertNotNull(roles);
        assertEquals(List.of("CUSTOMER"), roles, "O token deve conter a role CUSTOMER");

        // iss é entrego-auth
        assertEquals("entrego-auth", jwt.getClaimAsString("iss"), "O emissor 'iss' deve ser entrego-auth");

        // iat e exp
        assertNotNull(jwt.getIssuedAt(), "O token deve possuir timestamp de emissão 'iat'");
        assertNotNull(jwt.getExpiresAt(), "O token deve possuir timestamp de expiração 'exp'");

        // Expiração corresponde a 15 minutos (900 segundos)
        long validitySeconds = ChronoUnit.SECONDS.between(jwt.getIssuedAt(), jwt.getExpiresAt());
        assertEquals(900, validitySeconds, "A validade do Access Token deve ser de exatamente 900 segundos (15 minutos)");
    }

    @Test
    @DisplayName("Refresh token retornado é diferente do hash persistido e possui expiração de 30 dias")
    void shouldVerifyRefreshTokenHashedInDbAndHas30DaysExpiration() throws Exception {
        createTestUser("User RT", "userrt@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        LoginRequestDTO request = new LoginRequestDTO("userrt@example.com", "senha123@");

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponseDTO response = objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponseDTO.class);
        String rawRefreshToken = response.refreshToken();

        String expectedHash = hashToken(rawRefreshToken);
        RefreshToken persisted = refreshTokenRepository.findByTokenHash(expectedHash)
                .orElseThrow(() -> new AssertionError("Refresh token deve ter sido persistido com seu hash SHA-256"));

        // O token retornado é diferente do hash persistido
        assertNotEquals(rawRefreshToken, persisted.getTokenHash(),
                "O refresh token bruto retornado ao cliente NUNCA deve ser igual ao hash salvo no banco");

        // Expiração configurada de 30 dias
        long daysUntilExpiration = ChronoUnit.DAYS.between(persisted.getCreatedAt().toLocalDate(), persisted.getExpiresAt().toLocalDate());
        assertEquals(30, daysUntilExpiration, "O Refresh Token deve ter expiração configurada para 30 dias");
    }
}
