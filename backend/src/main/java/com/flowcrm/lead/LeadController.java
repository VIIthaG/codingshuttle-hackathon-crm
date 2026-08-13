package com.flowcrm.lead;

import com.flowcrm.common.exception.ErrorResponse;
import com.flowcrm.config.OpenApiConfig;
import com.flowcrm.enums.LeadStatus;
import com.flowcrm.idempotency.IdempotencyKeyValidator;
import com.flowcrm.lead.dto.LeadConvertRequest;
import com.flowcrm.lead.dto.LeadCreateRequest;
import com.flowcrm.lead.dto.LeadResponse;
import com.flowcrm.lead.dto.LeadStatusUpdateRequest;
import com.flowcrm.lead.dto.LeadUpdateRequest;
import com.flowcrm.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "Leads")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create lead",
            description = "Creates a lead. Optionally send Idempotency-Key for durable create idempotency.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lead created (or idempotent replay)"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed or blank Idempotency-Key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden (e.g. non-admin assigning to another user)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Idempotency-Key reused with a different request body",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public LeadResponse create(
            @Valid @RequestBody LeadCreateRequest request,
            @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            required = false,
                            description =
                                    "Optional. Same key + same body replays the original 201 response. "
                                            + "Same key + different body returns 409.",
                            schema = @Schema(type = "string", maxLength = 255, example = "demo-lead-001"))
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        String key = IdempotencyKeyValidator.normalizeOptional(idempotencyKey);
        if (key == null) {
            return leadService.create(request, principal);
        }
        return leadService.create(request, principal, key);
    }

    @GetMapping
    @Operation(summary = "List leads", description = "Paginated lead list. SALES_REP sees assigned leads only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of leads"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Page<LeadResponse> list(
            @Parameter(description = "Optional pipeline status filter")
                    @RequestParam(required = false)
                    LeadStatus status,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return leadService.list(status, principal, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get lead by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lead found"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Lead not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public LeadResponse getById(
            @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return leadService.getById(id, principal);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update lead")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lead updated"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed or invalid status transition",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Lead not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public LeadResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody LeadUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return leadService.update(id, request, principal);
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Change lead pipeline status",
            description = "Applies a validated pipeline transition (NEW → CONTACTED → QUALIFIED; LOST from active stages). CONVERTED is only set by POST /{id}/convert.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid status transition",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Lead not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public LeadResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody LeadStatusUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return leadService.changeStatus(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete lead")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Lead deleted"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Lead not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        leadService.delete(id, principal);
    }

    @PostMapping("/{id}/convert")
    @Operation(
            summary = "Convert a QUALIFIED lead",
            description =
                    "Atomically creates or reuses an account and contact, optionally a deal, then marks the lead CONVERTED. "
                            + "Optional Idempotency-Key. Same key + same lead + same body replays; different body or different lead id in the fingerprint returns 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lead converted (or idempotent replay)"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Lead not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Not QUALIFIED, already converted, or idempotency mismatch",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public LeadResponse convert(
            @PathVariable UUID id,
            @Valid @RequestBody LeadConvertRequest request,
            @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            required = false,
                            description =
                                    "Optional. Fingerprint includes lead id so a key cannot replay another lead's conversion.",
                            schema = @Schema(type = "string", maxLength = 255, example = "demo-convert-001"))
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        String key = IdempotencyKeyValidator.normalizeOptional(idempotencyKey);
        if (key == null) {
            return leadService.convert(id, request, principal);
        }
        return leadService.convert(id, request, principal, key);
    }
}
