package com.flowcrm.activity;

import com.flowcrm.activity.dto.ActivityTimelineResponse;
import com.flowcrm.common.exception.ErrorResponse;
import com.flowcrm.config.OpenApiConfig;
import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/activities")
@Tag(name = "Activities")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/timeline")
    @Operation(
            summary = "Get activity timeline",
            description =
                    "Aggregates record created/updated, lead conversion when applicable, and related tasks. "
                            + "Not a full audit log of historical status/stage transitions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Timeline for an accessible record"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Missing entityType or entityId",
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
                    description = "Record not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ActivityTimelineResponse timeline(
            @Parameter(description = "LEAD, ACCOUNT, CONTACT, or DEAL", required = true)
                    @RequestParam
                    RelatedRecordType entityType,
            @Parameter(description = "Record id", required = true) @RequestParam UUID entityId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return activityService.timeline(entityType, entityId, principal);
    }
}
