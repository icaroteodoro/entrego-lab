package br.com.icaroteodoro.entrego.auth.user.dtos;

import java.util.UUID;

public record UserResponseDTO(
        UUID id,
        String name,
        String email
) {
}
