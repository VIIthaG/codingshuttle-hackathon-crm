package com.flowcrm.lead.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Convert a QUALIFIED lead into an account, contact, and optional deal")
public record LeadConvertRequest(
        UUID useExistingAccountId,

        @Size(max = 255, message = "Account name must be at most 255 characters")
        String accountName,

        @Size(max = 255, message = "Website must be at most 255 characters")
        String accountWebsite,

        @Size(max = 50, message = "Phone must be at most 50 characters")
        String accountPhone,

        @Size(max = 255, message = "Industry must be at most 255 characters")
        String accountIndustry,

        UUID useExistingContactId,

        @Size(max = 255, message = "First name must be at most 255 characters")
        String contactFirstName,

        @Size(max = 255, message = "Last name must be at most 255 characters")
        String contactLastName,

        @Size(max = 255, message = "Email must be at most 255 characters")
        String contactEmail,

        @Size(max = 50, message = "Phone must be at most 50 characters")
        String contactPhone,

        @Size(max = 255, message = "Job title must be at most 255 characters")
        String contactJobTitle,

        Boolean createDeal,

        @Size(max = 255, message = "Deal name must be at most 255 characters")
        String dealName,

        @DecimalMin(value = "0.00", message = "Amount must be non-negative")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 17 integer digits and 2 decimal places")
        BigDecimal amount,

        @Pattern(regexp = "^$|^[A-Za-z]{3}$", message = "Currency must be a 3-letter code")
        String currency,

        LocalDate expectedCloseDate,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description) {
}
