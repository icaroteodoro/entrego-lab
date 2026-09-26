package br.com.icaroteodoro.entrego.auth;

import br.com.icaroteodoro.entrego.auth.auth.AuthService;
import br.com.icaroteodoro.entrego.auth.auth.dtos.LoginRequestDTO;
import br.com.icaroteodoro.entrego.auth.auth.dtos.LoginResponseDTO;
import br.com.icaroteodoro.entrego.auth.auth.dtos.RefreshTokenRequestDTO;
import br.com.icaroteodoro.entrego.auth.role.RoleName;
import br.com.icaroteodoro.entrego.auth.token.RefreshToken;
import br.com.icaroteodoro.entrego.auth.user.User;
import br.com.icaroteodoro.entrego.auth.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("3. Testes de Refresh Token")
class RefreshTokenTest extends BaseIntegrationTest {

    @Autowired
    private AuthService authService;

    @Test
    @DisplayName("Refresh token válido → gera novo access token e novo refresh token")
    void shouldGenerateNewAccessTokenAndNewRefreshTokenWhenValid() throws Exception {
        createTestUser("User Refresh", "refresh@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        // 1. Efetua Login
        LoginRequestDTO loginRequest = new LoginRequestDTO("refresh@example.com", "senha123@");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponseDTO loginResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponseDTO.class);
        String initialAccessToken = loginResponse.accessToken();
        String initialRefreshToken = loginResponse.refreshToken();

        // 2. Executa Refresh
        RefreshTokenRequestDTO refreshRequest = new RefreshTokenRequestDTO(initialRefreshToken);
        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponseDTO refreshResponse = objectMapper.readValue(refreshResult.getResponse().getContentAsString(), LoginResponseDTO.class);

        // Gera novo access token
        assertNotNull(refreshResponse.accessToken());
        assertFalse(refreshResponse.accessToken().isBlank());

        // Gera novo refresh token diferente do anterior
        assertNotNull(refreshResponse.refreshToken());
        assertNotEquals(initialRefreshToken, refreshResponse.refreshToken(), "O novo refresh token deve ser diferente do anterior");
    }

    @Test
    @DisplayName("Refresh token antigo é revogado depois da rotação e novo fica ativo")
    void shouldRevokeOldRefreshTokenAfterRotationAndKeepNewActive() throws Exception {
        createTestUser("User RTR", "rtr@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        LoginRequestDTO loginRequest = new LoginRequestDTO("rtr@example.com", "senha123@");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponseDTO loginResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponseDTO.class);
        String oldRawToken = loginResponse.refreshToken();

        // Executa rotação
        RefreshTokenRequestDTO refreshRequest = new RefreshTokenRequestDTO(oldRawToken);
        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponseDTO refreshResponse = objectMapper.readValue(refreshResult.getResponse().getContentAsString(), LoginResponseDTO.class);
        String newRawToken = refreshResponse.refreshToken();

        // Verifica que o token antigo foi revogado
        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(hashToken(oldRawToken)).orElseThrow();
        assertTrue(oldToken.isRevoked(), "O token antigo deve ter isRevoked() == true");
        assertNotNull(oldToken.getRevokedAt(), "O campo revokedAt do token antigo deve estar preenchido");

        // Verifica que o novo token está ativo no banco
        RefreshToken newToken = refreshTokenRepository.findByTokenHash(hashToken(newRawToken)).orElseThrow();
        assertFalse(newToken.isRevoked(), "O novo token não deve estar revogado");
        assertTrue(newToken.isValid(), "O novo token deve ser plenamente válido");

        // O novo token pode ser usado com sucesso para uma segunda rotação
        RefreshTokenRequestDTO secondRefreshRequest = new RefreshTokenRequestDTO(newRawToken);
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRefreshRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Tentar reutilizar refresh token revogado → 401")
    void shouldReturn401WhenReusingRevokedRefreshToken() throws Exception {
        createTestUser("User Replay", "replay@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        LoginRequestDTO loginRequest = new LoginRequestDTO("replay@example.com", "senha123@");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponseDTO loginResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponseDTO.class);
        String tokenRotacionado = loginResponse.refreshToken();

        // 1ª rotação consome o token com sucesso
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(tokenRotacionado))))
                .andExpect(status().isOk());

        // 2ª tentativa com o MESMO token antigo deve falhar com 401
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(tokenRotacionado))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
    }

    @Test
    @DisplayName("Refresh token expirado → 401")
    void shouldReturn401WhenRefreshTokenIsExpired() throws Exception {
        User user = createTestUser("Expirado", "expirado@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        // Cria token com data no passado (1 dia atrás)
        String rawToken = "token_expirado_mockado_1234567890123456789012";
        RefreshToken expiredToken = new RefreshToken(
                user,
                hashToken(rawToken),
                LocalDateTime.now().minusDays(1)
        );
        refreshTokenRepository.save(expiredToken);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(rawToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
    }

    @Test
    @DisplayName("Refresh token inexistente/inventado → 401")
    void shouldReturn401WhenRefreshTokenDoesNotExist() throws Exception {
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO("token_totalmente_inventado_que_nao_existe_no_banco");

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Refresh token vazio → 400")
    void shouldReturn400WhenRefreshTokenIsBlank() throws Exception {
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO("");

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Usuário deixou de estar ACTIVE depois do login → refresh rejeitado com 403")
    void shouldRejectRefreshWhenUserIsDeactivatedAfterLogin() throws Exception {
        User user = createTestUser("User Mudou Status", "mudoustatus@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        LoginRequestDTO loginRequest = new LoginRequestDTO("mudoustatus@example.com", "senha123@");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponseDTO loginResponse = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponseDTO.class);

        // Usuário é bloqueado após o login
        user.setStatus(UserStatus.BLOCKED);
        userRepository.save(user);

        // Tentativa de refresh deve ser barrada com 403
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequestDTO(loginResponse.refreshToken()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("User account is not active"));
    }

    @Test
    @DisplayName("Falha durante a rotação → transação anotada com @Transactional garante atomicidade")
    void shouldEnsureTransactionalAnnotationOnRefreshMethod() throws NoSuchMethodException {
        Method refreshMethod = AuthService.class.getMethod("refresh", RefreshTokenRequestDTO.class);
        assertTrue(refreshMethod.isAnnotationPresent(Transactional.class),
                "O método AuthService.refresh deve estar anotado com @Transactional para garantir atomicidade");
    }
}
