package br.com.icaroteodoro.entrego.auth.auth.dtos;

public record LoginResponseDTO(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType
) {
}