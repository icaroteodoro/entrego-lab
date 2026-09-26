package br.com.icaroteodoro.entrego.auth.auth.dtos;

import java.util.List;
import java.util.UUID;

public record MeResponseDTO(
        UUID id,
        String name,
        String email,
        List<String> roles
) {
}
