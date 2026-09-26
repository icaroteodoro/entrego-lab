package br.com.icaroteodoro.entrego.auth;

import br.com.icaroteodoro.entrego.auth.role.RoleName;
import br.com.icaroteodoro.entrego.auth.user.User;
import br.com.icaroteodoro.entrego.auth.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("5. Testes de Autorização e Roles (RBAC)")
class AuthorizationRolesTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Endpoint autenticado sem JWT → 401 padronizado com ApiError via CustomAuthenticationEntryPoint")
    void shouldReturn401WhenAccessingAuthenticatedEndpointWithoutJwt() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/auth/me"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        mockMvc.perform(get("/auth/customer-role-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/auth/customer-role-test"));

        mockMvc.perform(get("/auth/admin-role-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/auth/admin-role-test"));
    }

    @Test
    @DisplayName("Actuator /actuator/health está acessível publicamente para Docker e Kubernetes probes")
    void shouldAllowPublicAccessToActuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("JWT válido → acesso permitido")
    void shouldAllowAccessWhenJwtIsValid() throws Exception {
        User user = createTestUser("User Valid", "validjwt@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CUSTOMER acessa endpoint hasRole('CUSTOMER')")
    void shouldAllowCustomerRoleToAccessCustomerEndpoint() throws Exception {
        User customer = createTestUser("User Customer", "cust@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);
        String token = jwtService.generateAccessToken(customer);

        mockMvc.perform(get("/auth/customer-role-test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("Customer authorized"));
    }

    @Test
    @DisplayName("CUSTOMER não acessa endpoint hasRole('ADMIN') → 403 padronizado com ApiError via CustomAccessDeniedHandler")
    void shouldDenyCustomerFromAccessingAdminEndpointWith403() throws Exception {
        User customer = createTestUser("User Customer Blocked", "custblock@example.com", "senha123@", RoleName.CUSTOMER, UserStatus.ACTIVE);
        String token = jwtService.generateAccessToken(customer);

        mockMvc.perform(get("/auth/admin-role-test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions to access this resource"))
                .andExpect(jsonPath("$.path").value("/auth/admin-role-test"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("ADMIN acessa endpoint hasRole('ADMIN')")
    void shouldAllowAdminRoleToAccessAdminEndpoint() throws Exception {
        User admin = createTestUser("User Admin", "admin@example.com", "senha123@", RoleName.ADMIN, UserStatus.ACTIVE);
        String token = jwtService.generateAccessToken(admin);

        mockMvc.perform(get("/auth/admin-role-test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("Admin authorized"));
    }

    @Test
    @DisplayName("Usuário com múltiplas roles recebe todas no JWT e acessa ambos os endpoints")
    void shouldIncludeAllRolesInJwtWhenUserHasMultipleRoles() throws Exception {
        User multiRoleUser = createTestUserWithRoles(
                "Super Usuario",
                "super@example.com",
                "senha123@",
                UserStatus.ACTIVE,
                RoleName.CUSTOMER,
                RoleName.ADMIN
        );

        String token = jwtService.generateAccessToken(multiRoleUser);
        Jwt jwt = jwtDecoder.decode(token);

        List<String> roles = jwt.getClaimAsStringList("roles");
        assertTrue(roles.contains("CUSTOMER"), "Deve conter a role CUSTOMER");
        assertTrue(roles.contains("ADMIN"), "Deve conter a role ADMIN");

        // O JwtAuthenticationConverter mapeia roles para ROLE_CUSTOMER e ROLE_ADMIN
        mockMvc.perform(get("/auth/customer-role-test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("Customer authorized"));

        mockMvc.perform(get("/auth/admin-role-test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("Admin authorized"));
    }

    @Test
    @DisplayName("Endpoints públicos /auth/login, /register, /refresh e /logout funcionam sem JWT")
    void shouldEnsurePublicEndpointsAreAccessibleWithoutJwt() throws Exception {
        // /register funciona sem JWT (validação do body)
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()); // 400 por validação, NÃO 401

        // /login funciona sem JWT
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()); // 400 por validação, NÃO 401

        // /refresh funciona sem JWT
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()); // 400 por validação, NÃO 401

        // /logout funciona sem JWT
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()); // 400 por validação, NÃO 401
    }

    @Test
    @DisplayName("Endpoint protegido não fica público acidentalmente por alteração no SecurityConfig")
    void shouldEnsureProtectedEndpointsNeverBecomeAccidentallyPublic() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/auth/customer-role-test"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/auth/admin-role-test"))
                .andExpect(status().isUnauthorized());

        // Qualquer outra rota não mapeada explicitamente como permitAll exige autenticação
        mockMvc.perform(get("/auth/qualquer-outra-rota-inexistente"))
                .andExpect(status().isUnauthorized());
    }
}
