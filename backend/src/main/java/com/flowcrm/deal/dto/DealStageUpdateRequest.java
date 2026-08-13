package com.flowcrm.deal.dto;

import com.flowcrm.enums.DealStage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Deal pipeline stage transition")
public record DealStageUpdateRequest(
        @Schema(
                description = "Target stage. Allowed: PROSPECTING→QUALIFICATION→PROPOSAL→NEGOTIATION→CLOSED_WON; CLOSED_LOST from open stages.",
                example = "QUALIFICATION")
        @NotNull(message = "Stage is required")
        DealStage stage,

        @Size(max = 2000, message = "Lost reason must be at most 2000 characters")
        String lostReason,

        @Schema(description = "Optional probability override; ignored for terminal stages")
        @Min(value = 0, message = "Probability must be at least 0")
        @Max(value = 100, message = "Probability must be at most 100")
        Integer probability) {
}
