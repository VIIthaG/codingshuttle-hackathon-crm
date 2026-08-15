package com.flowcrm.analytics;

import com.flowcrm.analytics.dto.AnalyticsSummaryResponse;
import com.flowcrm.common.exception.ErrorResponse;
import com.flowcrm.config.OpenApiConfig;
import com.flowcrm.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Analytics summary",
            description =
                    """
                    Role-scoped CRM analytics. Uncached.

                    Date window (UTC): from inclusive, toExclusive exclusive (`from <= created_at < toExclusive`). \
                    Presets: `7d`, `30d` (default), `90d`, `all`. Last N days are calendar days including today UTC. \
                    Optional `from` + `toExclusive` ISO-8601 instants override the preset as CUSTOM.

                    Lead conversionRate = converted / (converted + lost) for the created-at cohort's **current** status; \
                    0 when the denominator is 0. Conversion trend uses `converted_at` (K3). Deal pipeline values are the \
                    current snapshot; there is no historical stage-transition series. Weighted pipeline = \
                    sum(open amount × probability / 100).

                    ADMIN default is the whole team; `assignedTo` narrows to one user. SALES_REP is always self-scoped; \
                    `assignedTo` for another user is rejected (403).
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Analytics summary"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid range",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "SALES_REP attempted another user's assignedTo filter",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AnalyticsSummaryResponse summary(
            @Parameter(description = "Preset: 7d, 30d, 90d, all") @RequestParam(required = false) String range,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant toExclusive,
            @Parameter(description = "ADMIN only: restrict to this assignee/owner") @RequestParam(required = false)
                    UUID assignedTo,
            @AuthenticationPrincipal UserPrincipal principal) {
        return analyticsService.summary(range, from, toExclusive, assignedTo, principal);
    }
}
