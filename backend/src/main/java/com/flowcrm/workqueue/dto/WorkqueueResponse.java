package com.flowcrm.workqueue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Deterministic next-actions view. No AI ranking.")
public record WorkqueueResponse(
        List<WorkqueueItemResponse> overdueTasks,
        List<WorkqueueItemResponse> dueTodayTasks,
        List<WorkqueueItemResponse> upcomingTasks,
        List<WorkqueueItemResponse> todayMeetings,
        List<WorkqueueItemResponse> upcomingMeetings,
        List<WorkqueueItemResponse> todayCalls,
        List<WorkqueueItemResponse> upcomingCalls,
        WorkqueueCounts counts) {

    public record WorkqueueCounts(
            int overdueTasks,
            int dueTodayTasks,
            int upcomingTasks,
            int todayMeetings,
            int upcomingMeetings,
            int todayCalls,
            int upcomingCalls) {}
}
