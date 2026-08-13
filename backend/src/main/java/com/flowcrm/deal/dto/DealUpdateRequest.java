package com.flowcrm.deal.dto;

import com.flowcrm.enums.DealStage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Full deal update request")
public record DealUpdateRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @NotNull(message = "Account is required")
        UUID accountId,

        UUID primaryContactId,

        @Schema(description = "Owner user id")
        @NotNull(message = "Owner is required")
        UUID ownerId,

        @Schema(description = "Target stage; must be current stage or a valid transition")
        @NotNull(message = "Stage is required")
        DealStage stage,

        @DecimalMin(value = "0.00", message = "Amount must be non-negative")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 17 integer digits and 2 decimal places")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter code")
        String currency,

        @Min(value = 0, message = "Probability must be at least 0")
        @Max(value = 100, message = "Probability must be at most 100")
        Integer probability,

        LocalDate expectedCloseDate,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @Size(max = 2000, message = "Lost reason must be at most 2000 characters")
        String lostReason) {
}
