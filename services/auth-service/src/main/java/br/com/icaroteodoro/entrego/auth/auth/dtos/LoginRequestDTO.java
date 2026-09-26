package br.com.icaroteodoro.entrego.auth.auth.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank
        @Email
        String email,

        @NotBlank
        String password
) {
}
