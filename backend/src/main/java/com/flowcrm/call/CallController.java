package com.flowcrm.call;

import com.flowcrm.call.dto.CallCreateRequest;
import com.flowcrm.call.dto.CallResponse;
import com.flowcrm.call.dto.CallStatusUpdateRequest;
import com.flowcrm.call.dto.CallUpdateRequest;
import com.flowcrm.config.OpenApiConfig;
import com.flowcrm.enums.CallDirection;
import com.flowcrm.enums.CallStatus;
import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.idempotency.IdempotencyKeyValidator;
import com.flowcrm.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
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
@RequestMapping("/api/v1/calls")
@Tag(name = "Calls")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class CallController {

    private final CallService callService;

    public CallController(CallService callService) {
        this.callService = callService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create call", description = "Optional Idempotency-Key. Exactly one related CRM record.")
    public CallResponse create(
            @Valid @RequestBody CallCreateRequest request,
            @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = false)
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        String key = IdempotencyKeyValidator.normalizeOptional(idempotencyKey);
        if (key == null) {
            return callService.create(request, principal);
        }
        return callService.create(request, principal, key);
    }

    @GetMapping
    @Operation(summary = "List calls")
    public Page<CallResponse> list(
            @RequestParam(required = false) CallStatus status,
            @RequestParam(required = false) CallDirection direction,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) UUID dealId,
            @RequestParam(required = false) RelatedRecordType relatedType,
            @RequestParam(required = false) UUID assignedToId,
            @RequestParam(required = false) Instant scheduledFrom,
            @RequestParam(required = false) Instant scheduledTo,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return callService.list(
                status,
                direction,
                leadId,
                accountId,
                contactId,
                dealId,
                relatedType,
                assignedToId,
                scheduledFrom,
                scheduledTo,
                principal,
                pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get call")
    public CallResponse getById(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return callService.getById(id, principal);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update call")
    public CallResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody CallUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return callService.update(id, request, principal);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change call status", description = "PLANNED → COMPLETED or CANCELLED. Optional outcome on complete.")
    public CallResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody CallStatusUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return callService.changeStatus(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete call")
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        callService.delete(id, principal);
    }
}
