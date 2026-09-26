package br.com.icaroteodoro.entrego.auth.auth.dtos;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDTO(
        @NotBlank
        String refreshToken
) {
}
