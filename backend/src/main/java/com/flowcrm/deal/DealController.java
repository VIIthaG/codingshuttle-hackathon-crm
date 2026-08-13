package com.flowcrm.deal;

import com.flowcrm.common.exception.ErrorResponse;
import com.flowcrm.config.OpenApiConfig;
import com.flowcrm.deal.dto.DealCreateRequest;
import com.flowcrm.deal.dto.DealResponse;
import com.flowcrm.deal.dto.DealStageUpdateRequest;
import com.flowcrm.deal.dto.DealUpdateRequest;
import com.flowcrm.enums.DealStage;
import com.flowcrm.idempotency.IdempotencyKeyValidator;
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
import java.time.LocalDate;
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
@RequestMapping("/api/v1/deals")
@Tag(name = "Deals")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class DealController {

    private final DealService dealService;

    public DealController(DealService dealService) {
        this.dealService = dealService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create deal",
            description = "Creates a deal. Optionally send Idempotency-Key for durable create idempotency.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Deal created (or idempotent replay)"),
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
                    description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Idempotency-Key reused with a different request body",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public DealResponse create(
            @Valid @RequestBody DealCreateRequest request,
            @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            required = false,
                            description =
                                    "Optional. Same key + same body replays the original 201 response. "
                                            + "Same key + different body returns 409.",
                            schema = @Schema(type = "string", maxLength = 255, example = "demo-deal-001"))
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        String key = IdempotencyKeyValidator.normalizeOptional(idempotencyKey);
        if (key == null) {
            return dealService.create(request, principal);
        }
        return dealService.create(request, principal, key);
    }

    @GetMapping
    @Operation(summary = "List deals", description = "Paginated deals. SALES_REP sees owned deals only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of deals"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Page<DealResponse> list(
            @Parameter(description = "Search deal name, account name, or primary contact name")
                    @RequestParam(required = false)
                    String search,
            @RequestParam(required = false) DealStage stage,
            @RequestParam(required = false) UUID accountId,
            @Parameter(description = "ADMIN only: filter by owner") @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) LocalDate expectedCloseFrom,
            @RequestParam(required = false) LocalDate expectedCloseTo,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return dealService.list(
                search, stage, accountId, ownerId, expectedCloseFrom, expectedCloseTo, principal, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get deal by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deal found"),
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
                    description = "Deal not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public DealResponse getById(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return dealService.getById(id, principal);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update deal")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deal updated"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed or invalid stage transition",
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
                    description = "Deal not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public DealResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody DealUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return dealService.update(id, request, principal);
    }

    @PatchMapping("/{id}/stage")
    @Operation(
            summary = "Change deal pipeline stage",
            description =
                    "Applies a validated transition: PROSPECTING→QUALIFICATION→PROPOSAL→NEGOTIATION→CLOSED_WON; CLOSED_LOST from open stages.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stage updated"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid stage transition",
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
                    description = "Deal not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public DealResponse changeStage(
            @PathVariable UUID id,
            @Valid @RequestBody DealStageUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return dealService.changeStage(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete deal")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deal deleted"),
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
                    description = "Deal not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        dealService.delete(id, principal);
    }
}
