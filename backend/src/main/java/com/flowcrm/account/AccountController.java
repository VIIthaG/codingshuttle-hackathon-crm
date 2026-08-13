package com.flowcrm.account;

import com.flowcrm.account.dto.AccountCreateRequest;
import com.flowcrm.account.dto.AccountResponse;
import com.flowcrm.account.dto.AccountUpdateRequest;
import com.flowcrm.common.exception.ErrorResponse;
import com.flowcrm.config.OpenApiConfig;
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
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create account",
            description = "Creates a company/account. Optionally send Idempotency-Key for durable create idempotency.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created (or idempotent replay)"),
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
    public AccountResponse create(
            @Valid @RequestBody AccountCreateRequest request,
            @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            required = false,
                            description =
                                    "Optional. Same key + same body replays the original 201 response. "
                                            + "Same key + different body returns 409.",
                            schema = @Schema(type = "string", maxLength = 255, example = "demo-account-001"))
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        String key = IdempotencyKeyValidator.normalizeOptional(idempotencyKey);
        if (key == null) {
            return accountService.create(request, principal);
        }
        return accountService.create(request, principal, key);
    }

    @GetMapping
    @Operation(summary = "List accounts", description = "Paginated accounts. SALES_REP sees owned accounts only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of accounts"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Page<AccountResponse> list(
            @Parameter(description = "Search name, website, or industry") @RequestParam(required = false) String search,
            @Parameter(description = "ADMIN only: filter by owner") @RequestParam(required = false) UUID ownerId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return accountService.list(search, ownerId, principal, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account found"),
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
                    description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AccountResponse getById(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return accountService.getById(id, principal);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account updated"),
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
                    description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AccountResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody AccountUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return accountService.update(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete account")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Account deleted"),
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
                    description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        accountService.delete(id, principal);
    }
}
