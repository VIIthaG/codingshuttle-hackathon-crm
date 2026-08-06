package com.flowcrm.auth.dto;

import com.flowcrm.enums.Role;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        Role role) {
}
