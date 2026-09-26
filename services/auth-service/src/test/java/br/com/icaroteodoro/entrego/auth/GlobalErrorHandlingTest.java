package br.com.icaroteodoro.entrego.auth;

import br.com.icaroteodoro.entrego.auth.auth.dtos.LoginRequestDTO;
import br.com.icaroteodoro.entrego.auth.auth.dtos.RefreshTokenRequestDTO;
import br.com.icaroteodoro.entrego.auth.role.RoleName;
import br.com.icaroteodoro.entrego.auth.user.UserStatus;
import br.com.icaroteodoro.entrego.auth.user.dtos.CreateUserRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("8. Testes de Tratamento Global de Erros (ApiError)")
class GlobalErrorHandlingTest extends BaseIntegrationTest {

    @Test
    @DisplayName("BadCredentialsException → 401 com estrutura ApiError completa")
    void shouldReturn401WithApiErrorStructureOnBadCredentials() throws Exception {
        createTestUser("User Err", "usererr@example.com", "senhaCorreta123@", RoleName.CUSTOMER, UserStatus.ACTIVE);

        LoginRequestDTO request = new LoginRequestDTO("usererr@example.com", "senhaIncorreta");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.path").value("/auth/login"))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    @DisplayName("InvalidRefreshTokenException → 401 com estrutura ApiError completa")
    void shouldReturn401WithApiErrorStructureOnInvalidRefreshToken() throws Exception {
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO("token_totalmente_falso");

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid refresh token"))
                .andExpect(jsonPath("$.path").value("/auth/refresh"))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("Usuário não ativo → 403 Forbidden com estrutura ApiError completa")
    void shouldReturn403WithApiErrorStructureWhenUserIsNotActive() throws Exception {
        createTestUser("User Bloqueado", "bloqueado_err@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.BLOCKED);

        LoginRequestDTO request = new LoginRequestDTO("bloqueado_err@example.com", "senha123@");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("User account is not active"))
                .andExpect(jsonPath("$.path").value("/auth/login"))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("Falha de validação Bean Validation (@Valid) → 400 Bad Request com estrutura ApiError")
    void shouldReturn400WithApiErrorStructureOnValidationFailure() throws Exception {
        CreateUserRequestDTO request = new CreateUserRequestDTO(
                "",
                "email_invalido",
                "123"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.path").value("/auth/register"))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
}
