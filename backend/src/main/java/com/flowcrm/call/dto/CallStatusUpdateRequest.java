package com.flowcrm.call.dto;

import com.flowcrm.enums.CallStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Change call lifecycle status. outcome is stored when completing.")
public record CallStatusUpdateRequest(
        @NotNull CallStatus status, @Size(max = 2000) String outcome) {}
