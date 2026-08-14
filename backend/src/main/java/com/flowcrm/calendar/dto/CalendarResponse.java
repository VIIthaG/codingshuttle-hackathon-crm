package com.flowcrm.calendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Calendar window of accessible scheduled work")
public record CalendarResponse(Instant from, Instant to, List<CalendarItemResponse> items) {}
