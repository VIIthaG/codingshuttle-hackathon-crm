package com.flowcrm.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "JWT authentication response")
public record AuthResponse(
        @Schema(description = "JWT access token to use as Bearer authorization", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "Token type", example = "Bearer")
        String tokenType,

        @Schema(description = "Token lifetime in seconds", example = "86400")
        long expiresIn,

        @Schema(description = "Authenticated user profile")
        UserResponse user) {
}
