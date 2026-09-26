package br.com.icaroteodoro.entrego.auth;

import br.com.icaroteodoro.entrego.auth.role.Role;
import br.com.icaroteodoro.entrego.auth.role.RoleName;
import br.com.icaroteodoro.entrego.auth.user.User;
import br.com.icaroteodoro.entrego.auth.user.UserStatus;
import br.com.icaroteodoro.entrego.auth.user.dtos.CreateUserRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("1. Testes de Cadastro de Usuário")
class UserRegistrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Cadastro de usuário com dados válidos → cria usuário com sucesso")
    void shouldCreateUserSuccessfullyWhenDataIsValid() throws Exception {
        CreateUserRequestDTO request = new CreateUserRequestDTO(
                "Maria Silva",
                "maria.silva@example.com",
                "senhaSegura123@"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value("maria.silva@example.com"));

        Optional<User> saved = userRepository.findByEmailIgnoreCase("maria.silva@example.com");
        assertTrue(saved.isPresent(), "Usuário deve estar persistido no banco");
        assertEquals("Maria Silva", saved.get().getName());
    }

    @Test
    @DisplayName("Novo usuário recebe CUSTOMER automaticamente")
    void shouldAssignCustomerRoleAutomatically() throws Exception {
        CreateUserRequestDTO request = new CreateUserRequestDTO(
                "Carlos Souza",
                "carlos@example.com",
                "senhaSegura123@"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmailIgnoreCase("carlos@example.com")
                .orElseThrow();

        Set<Role> roles = user.getRoles();
        assertEquals(1, roles.size(), "Usuário deve possuir exatamente 1 role");
        assertEquals(RoleName.CUSTOMER, roles.iterator().next().getName());
    }

    @Test
    @DisplayName("Senha é persistida com hash BCrypt, nunca em texto puro")
    void shouldPersistPasswordAsBCryptHashNeverPlainText() throws Exception {
        String plainPassword = "senhaSegura123@";
        CreateUserRequestDTO request = new CreateUserRequestDTO(
                "Ana Costa",
                "ana.costa@example.com",
                plainPassword
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmailIgnoreCase("ana.costa@example.com")
                .orElseThrow();

        assertNotEquals(plainPassword, user.getPassword(), "A senha não pode estar em texto puro");
        assertTrue(user.getPassword().startsWith("$2a$") || user.getPassword().startsWith("$2b$") || user.getPassword().startsWith("$2y$"),
                "A senha deve iniciar com o identificador de hash BCrypt ($2a$, $2b$ ou $2y$)");
        assertTrue(passwordEncoder.matches(plainPassword, user.getPassword()),
                "BCryptPasswordEncoder deve validar com sucesso o hash gerado");
    }

    @Test
    @DisplayName("E-mail já cadastrado → rejeita cadastro")
    void shouldRejectRegistrationWhenEmailAlreadyExists() throws Exception {
        CreateUserRequestDTO request = new CreateUserRequestDTO(
                "Lucas Lima",
                "lucas@example.com",
                "senhaSegura123@"
        );

        // Primeiro cadastro deve passar
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Segunda tentativa com o mesmo e-mail deve ser rejeitada
        try {
            MvcResult result = mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            assertTrue(result.getResponse().getStatus() >= 400, "Cadastro duplicado deve ser rejeitado com erro HTTP");
        } catch (Exception ex) {
            // Em caso de exceção de negócio propagada no MockMvc
            assertTrue(ex instanceof IllegalAccessException || (ex.getCause() != null && ex.getCause() instanceof IllegalAccessException),
                    "Deve disparar IllegalAccessException informando duplicidade de e-mail");
        }
    }

    @Test
    @DisplayName("E-mail inválido → 400")
    void shouldReturn400WhenEmailIsInvalid() throws Exception {
        CreateUserRequestDTO request = new CreateUserRequestDTO(
                "Formato Invalido",
                "email-sem-arroba-e-dominio",
                "senhaSegura123@"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("email")));
    }

    @Test
    @DisplayName("Campos obrigatórios ausentes/vazios → 400")
    void shouldReturn400WhenRequiredFieldsAreMissingOrBlank() throws Exception {
        // Nome em branco
        CreateUserRequestDTO missingName = new CreateUserRequestDTO(
                "",
                "valido@example.com",
                "senhaSegura123@"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Senha com tamanho insuficiente (< 8 caracteres)
        CreateUserRequestDTO shortPassword = new CreateUserRequestDTO(
                "Teste",
                "valido2@example.com",
                "123"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Usuário não consegue escolher ADMIN ou STORE_OWNER durante o cadastro")
    void shouldNotAllowUserToChoosePrivilegedRolesDuringRegistration() throws Exception {
        // Envia JSON com injeção de roles privilegiadas
        String rawJson = """
                {
                    "name": "Tentativa Privilege Escalation",
                    "email": "hacker@example.com",
                    "password": "senhaSegura123@",
                    "roles": ["ADMIN", "STORE_OWNER"],
                    "role": "ADMIN"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isOk());

        User user = userRepository.findByEmailIgnoreCase("hacker@example.com")
                .orElseThrow();

        // Garante que nenhuma role de privilégio foi atribuída
        Set<Role> roles = user.getRoles();
        assertEquals(1, roles.size());
        assertEquals(RoleName.CUSTOMER, roles.iterator().next().getName());
        assertFalse(roles.stream().anyMatch(r -> r.getName() == RoleName.ADMIN));
        assertFalse(roles.stream().anyMatch(r -> r.getName() == RoleName.STORE_OWNER));
    }

    @Test
    @DisplayName("Novo usuário inicia com UserStatus.ACTIVE conforme nossa regra atual")
    void shouldStartNewUserWithActiveStatus() throws Exception {
        CreateUserRequestDTO request = new CreateUserRequestDTO(
                "Usuario Ativo",
                "ativo@example.com",
                "senhaSegura123@"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmailIgnoreCase("ativo@example.com")
                .orElseThrow();

        assertEquals(UserStatus.ACTIVE, user.getStatus(), "Novo usuário deve iniciar com status ACTIVE");
    }
}
