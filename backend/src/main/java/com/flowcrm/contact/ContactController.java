package com.flowcrm.contact;

import com.flowcrm.common.exception.ErrorResponse;
import com.flowcrm.config.OpenApiConfig;
import com.flowcrm.contact.dto.ContactCreateRequest;
import com.flowcrm.contact.dto.ContactResponse;
import com.flowcrm.contact.dto.ContactUpdateRequest;
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
@RequestMapping("/api/v1/contacts")
@Tag(name = "Contacts")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create contact",
            description = "Creates a contact. Optionally send Idempotency-Key for durable create idempotency.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Contact created (or idempotent replay)"),
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
                    responseCode = "404",
                    description = "Account not found / not accessible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Idempotency-Key reused with a different request body",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ContactResponse create(
            @Valid @RequestBody ContactCreateRequest request,
            @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            required = false,
                            description =
                                    "Optional. Same key + same body replays the original 201 response. "
                                            + "Same key + different body returns 409.",
                            schema = @Schema(type = "string", maxLength = 255, example = "demo-contact-001"))
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        String key = IdempotencyKeyValidator.normalizeOptional(idempotencyKey);
        if (key == null) {
            return contactService.create(request, principal);
        }
        return contactService.create(request, principal, key);
    }

    @GetMapping
    @Operation(summary = "List contacts", description = "Paginated contacts. SALES_REP sees owned contacts only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of contacts"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Page<ContactResponse> list(
            @Parameter(description = "Search name, email, phone, or job title")
                    @RequestParam(required = false)
                    String search,
            @Parameter(description = "Filter by account id") @RequestParam(required = false) UUID accountId,
            @Parameter(description = "ADMIN only: filter by owner") @RequestParam(required = false) UUID ownerId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return contactService.list(search, accountId, ownerId, principal, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get contact by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contact found"),
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
                    description = "Contact not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ContactResponse getById(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return contactService.getById(id, principal);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update contact")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contact updated"),
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
                    description = "Contact or account not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ContactResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody ContactUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return contactService.update(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete contact")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contact deleted"),
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
                    description = "Contact not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        contactService.delete(id, principal);
    }
}
