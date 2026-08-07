package com.flowcrm.auth.dto;

import com.flowcrm.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "User profile")
public record UserResponse(
        @Schema(description = "User id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(example = "alice@example.com")
        String email,

        @Schema(example = "Alice Admin")
        String fullName,

        @Schema(description = "Role used for RBAC", example = "ADMIN")
        Role role) {
}
