package com.flowcrm.calendar;

import com.flowcrm.calendar.dto.CalendarResponse;
import com.flowcrm.config.OpenApiConfig;
import com.flowcrm.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/calendar")
@Tag(name = "Calendar")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping
    @Operation(
            summary = "List scheduled CRM work",
            description =
                    "Aggregates OPEN tasks (dueAt), SCHEDULED meetings (startAt), and PLANNED calls (scheduledAt). "
                            + "Completed/cancelled items are excluded. Default window is the current UTC month.")
    public CalendarResponse list(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID assignedToId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return calendarService.list(from, to, assignedToId, principal);
    }
}
