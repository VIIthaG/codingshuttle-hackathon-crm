package com.flowcrm.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Health probe response")
public record HealthResponse(
        @Schema(example = "UP") String status,
        @Schema(example = "flowcrm") String application) {
}
