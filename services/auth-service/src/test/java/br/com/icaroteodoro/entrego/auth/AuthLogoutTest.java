package br.com.icaroteodoro.entrego.auth;

import br.com.icaroteodoro.entrego.auth.auth.dtos.LoginRequestDTO;
import br.com.icaroteodoro.entrego.auth.auth.dtos.LoginResponseDTO;
import br.com.icaroteodoro.entrego.auth.auth.dtos.LogoutRequestDTO;
import br.com.icaroteodoro.entrego.auth.auth.dtos.RefreshTokenRequestDTO;
import br.com.icaroteodoro.entrego.auth.role.RoleName;
import br.com.icaroteodoro.entrego.auth.token.RefreshToken;
import br.com.icaroteodoro.entrego.auth.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("4. Testes de Logout")
class AuthLogoutTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Refresh token válido → 204")
    void shouldReturn204WhenLogoutWithValidRefreshToken() throws Exception {
        createTestUser("User Logout", "logout@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        LoginRequestDTO loginRequest = new LoginRequestDTO("logout@example.com", "senha123@");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponseDTO loginResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponseDTO.class);

        LogoutRequestDTO logoutRequest = new LogoutRequestDTO(loginResponse.refreshToken());

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Logout marca revoked_at no banco de dados")
    void shouldMarkRevokedAtWhenLoggingOut() throws Exception {
        createTestUser("User Revoke", "revoke@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        LoginRequestDTO loginRequest = new LoginRequestDTO("revoke@example.com", "senha123@");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponseDTO loginResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponseDTO.class);
        String rawToken = loginResponse.refreshToken();

        // Antes do logout, não deve estar revogado
        RefreshToken tokenBefore = refreshTokenRepository.findByTokenHash(hashToken(rawToken)).orElseThrow();
        assertFalse(tokenBefore.isRevoked());
        assertNull(tokenBefore.getRevokedAt());

        // Executa logout
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequestDTO(rawToken))))
                .andExpect(status().isNoContent());

        // Após logout, deve ter revoked_at preenchido
        RefreshToken tokenAfter = refreshTokenRepository.findByTokenHash(hashToken(rawToken)).orElseThrow();
        assertTrue(tokenAfter.isRevoked(), "O token deve estar marcado como revogado");
        assertNotNull(tokenAfter.getRevokedAt(), "O campo revokedAt deve estar preenchido");
    }

    @Test
    @DisplayName("Depois do logout, aquele refresh token não pode renovar a sessão")
    void shouldPreventRefreshTokenFromRenewingSessionAfterLogout() throws Exception {
        createTestUser("User NoRenew", "norenew@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        LoginRequestDTO loginRequest = new LoginRequestDTO("norenew@example.com", "senha123@");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponseDTO loginResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponseDTO.class);
        String rawToken = loginResponse.refreshToken();

        // Realiza logout
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequestDTO(rawToken))))
                .andExpect(status().isNoContent());

        // Tenta renovar sessão com o token que fez logout -> 401
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(rawToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
    }

    @Test
    @DisplayName("Fazer logout novamente com token já revogado continua sendo operação segura e idempotente")
    void shouldBeSafeAndIdempotentWhenLoggingOutAlreadyRevokedToken() throws Exception {
        createTestUser("User Idempotent", "idempotent@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        LoginRequestDTO loginRequest = new LoginRequestDTO("idempotent@example.com", "senha123@");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponseDTO loginResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponseDTO.class);
        String rawToken = loginResponse.refreshToken();

        // Primeiro logout -> 204
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequestDTO(rawToken))))
                .andExpect(status().isNoContent());

        // Segundo logout com o mesmo token já revogado -> continua retornando 204
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequestDTO(rawToken))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Refresh token inexistente → resposta 401 definida pela nossa API")
    void shouldReturn401WhenLoggingOutNonExistentToken() throws Exception {
        LogoutRequestDTO request = new LogoutRequestDTO("token_totalmente_inexistente_no_sistema");

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    @DisplayName("Logout não cria novos tokens")
    void shouldNotCreateAnyNewTokensDuringLogout() throws Exception {
        createTestUser("User NoNewTokens", "nonew@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        LoginRequestDTO loginRequest = new LoginRequestDTO("nonew@example.com", "senha123@");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponseDTO loginResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponseDTO.class);

        long countBefore = refreshTokenRepository.count();

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequestDTO(loginResponse.refreshToken()))))
                .andExpect(status().isNoContent());

        long countAfter = refreshTokenRepository.count();
        assertEquals(countBefore, countAfter, "Nenhum novo registro de token deve ser criado durante o logout");
    }

    @Test
    @DisplayName("Access token existente continua válido até sua expiração, conforme nossa arquitetura stateless")
    void shouldKeepExistingAccessTokenValidUntilExpirationEvenAfterLogout() throws Exception {
        createTestUser("User Stateless", "stateless@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        LoginRequestDTO loginRequest = new LoginRequestDTO("stateless@example.com", "senha123@");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponseDTO loginResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponseDTO.class);
        String accessToken = loginResponse.accessToken();
        String refreshToken = loginResponse.refreshToken();

        // Faz logout do refresh token
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequestDTO(refreshToken))))
                .andExpect(status().isNoContent());

        // O access token continua válido em rotas autenticadas até expirar (arquitetura stateless)
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("stateless@example.com"));
    }
}
