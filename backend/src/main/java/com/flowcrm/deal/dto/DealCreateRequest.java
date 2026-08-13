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

@Schema(description = "Create deal request")
public record DealCreateRequest(
        @Schema(example = "Acme expansion")
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @Schema(description = "Required account id")
        @NotNull(message = "Account is required")
        UUID accountId,

        @Schema(description = "Optional primary contact")
        UUID primaryContactId,

        @Schema(description = "Optional owner; defaults to current user. Only ADMIN may assign others.")
        UUID ownerId,

        @Schema(description = "Optional initial stage; defaults to PROSPECTING", example = "PROSPECTING")
        DealStage stage,

        @Schema(example = "25000.00")
        @DecimalMin(value = "0.00", message = "Amount must be non-negative")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 17 integer digits and 2 decimal places")
        BigDecimal amount,

        @Schema(description = "ISO-4217 currency code; defaults to USD", example = "USD")
        @Pattern(regexp = "^$|^[A-Za-z]{3}$", message = "Currency must be a 3-letter code")
        String currency,

        @Schema(description = "Optional probability 0-100; defaults from stage. Terminal stages force 100/0.")
        @Min(value = 0, message = "Probability must be at least 0")
        @Max(value = 100, message = "Probability must be at most 100")
        Integer probability,

        LocalDate expectedCloseDate,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        @Size(max = 2000, message = "Lost reason must be at most 2000 characters")
        String lostReason) {
}
