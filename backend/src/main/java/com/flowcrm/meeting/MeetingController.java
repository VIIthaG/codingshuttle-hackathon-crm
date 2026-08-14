package com.flowcrm.meeting;

import com.flowcrm.config.OpenApiConfig;
import com.flowcrm.enums.MeetingStatus;
import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.idempotency.IdempotencyKeyValidator;
import com.flowcrm.meeting.dto.MeetingCreateRequest;
import com.flowcrm.meeting.dto.MeetingResponse;
import com.flowcrm.meeting.dto.MeetingStatusUpdateRequest;
import com.flowcrm.meeting.dto.MeetingUpdateRequest;
import com.flowcrm.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@RequestMapping("/api/v1/meetings")
@Tag(name = "Meetings")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create meeting", description = "Optional Idempotency-Key. Exactly one related CRM record.")
    public MeetingResponse create(
            @Valid @RequestBody MeetingCreateRequest request,
            @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = false)
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        String key = IdempotencyKeyValidator.normalizeOptional(idempotencyKey);
        if (key == null) {
            return meetingService.create(request, principal);
        }
        return meetingService.create(request, principal, key);
    }

    @GetMapping
    @Operation(summary = "List meetings")
    public Page<MeetingResponse> list(
            @RequestParam(required = false) MeetingStatus status,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) UUID dealId,
            @RequestParam(required = false) RelatedRecordType relatedType,
            @RequestParam(required = false) UUID assignedToId,
            @RequestParam(required = false) Instant startFrom,
            @RequestParam(required = false) Instant startTo,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return meetingService.list(
                status,
                leadId,
                accountId,
                contactId,
                dealId,
                relatedType,
                assignedToId,
                startFrom,
                startTo,
                principal,
                pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get meeting")
    public MeetingResponse getById(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return meetingService.getById(id, principal);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update meeting")
    public MeetingResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody MeetingUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return meetingService.update(id, request, principal);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change meeting status", description = "SCHEDULED → COMPLETED or CANCELLED. Terminal states cannot be reopened.")
    public MeetingResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody MeetingStatusUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return meetingService.changeStatus(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete meeting")
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        meetingService.delete(id, principal);
    }
}
