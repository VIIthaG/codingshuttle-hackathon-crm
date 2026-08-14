package com.flowcrm.meeting.dto;

import com.flowcrm.enums.MeetingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Change meeting lifecycle status")
public record MeetingStatusUpdateRequest(@NotNull MeetingStatus status) {}
