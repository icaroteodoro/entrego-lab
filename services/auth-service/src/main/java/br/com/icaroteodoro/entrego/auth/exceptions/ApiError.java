package br.com.icaroteodoro.entrego.auth.exceptions;

import java.time.LocalDateTime;

public record ApiError(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp
) {
}
