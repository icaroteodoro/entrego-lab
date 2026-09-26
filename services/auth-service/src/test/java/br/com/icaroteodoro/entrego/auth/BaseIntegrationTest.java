package br.com.icaroteodoro.entrego.auth;

import br.com.icaroteodoro.entrego.auth.role.Role;
import br.com.icaroteodoro.entrego.auth.role.RoleName;
import br.com.icaroteodoro.entrego.auth.role.RoleRepository;
import br.com.icaroteodoro.entrego.auth.security.JwtService;
import br.com.icaroteodoro.entrego.auth.token.RefreshTokenRepository;
import br.com.icaroteodoro.entrego.auth.token.RefreshTokenService;
import br.com.icaroteodoro.entrego.auth.user.User;
import br.com.icaroteodoro.entrego.auth.user.UserRepository;
import br.com.icaroteodoro.entrego.auth.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected RefreshTokenService refreshTokenService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected JwtDecoder jwtDecoder;

    @Autowired
    protected JwtEncoder jwtEncoder;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM refresh_tokens");
        jdbcTemplate.execute("DELETE FROM user_roles");
        jdbcTemplate.execute("DELETE FROM users");
    }

    protected User createTestUser(String name, String email, String rawPassword, RoleName roleName, UserStatus status) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found in seed: " + roleName));

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setStatus(status);
        user.addRole(role);

        return userRepository.save(user);
    }

    protected User createTestUserWithRoles(String name, String email, String rawPassword, UserStatus status, RoleName... roleNames) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setStatus(status);

        for (RoleName rName : roleNames) {
            Role role = roleRepository.findByName(rName)
                    .orElseThrow(() -> new IllegalStateException("Role not found in seed: " + rName));
            user.addRole(role);
        }

        return userRepository.save(user);
    }

    protected String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
