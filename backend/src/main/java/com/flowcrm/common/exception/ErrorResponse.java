package com.flowcrm.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Standard API error body")
public record ErrorResponse(
        @Schema(description = "Error timestamp (UTC)")
        Instant timestamp,

        @Schema(description = "HTTP status code")
        int status,

        @Schema(description = "Error category (for example Bad Request, Unauthorized, Conflict)")
        String error,

        @Schema(description = "Human-readable message")
        String message,

        @Schema(description = "Optional field-level validation errors")
        Map<String, String> fieldErrors) {
}
