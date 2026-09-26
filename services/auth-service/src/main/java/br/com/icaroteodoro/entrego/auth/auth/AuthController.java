package br.com.icaroteodoro.entrego.auth.auth;

import br.com.icaroteodoro.entrego.auth.auth.dtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO request
    ) {
        return ResponseEntity.ok(
                authService.login(request)
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(
            @Valid @RequestBody RefreshTokenRequestDTO request
    ) {
        return ResponseEntity.ok(
                authService.refresh(request)
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody LogoutRequestDTO request
    ) {
        authService.logout(request);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponseDTO> me(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        return ResponseEntity.ok(
                authService.me(userId)
        );
    }

    @GetMapping("/customer-role-test")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String customerTest() {
        return "Customer authorized";
    }

    @GetMapping("/admin-role-test")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminTest() {
        return "Admin authorized";
    }
}
