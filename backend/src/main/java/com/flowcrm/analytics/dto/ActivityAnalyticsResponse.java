package com.flowcrm.analytics.dto;

public record ActivityAnalyticsResponse(
        TaskActivityMetricsResponse tasks,
        MeetingActivityMetricsResponse meetings,
        CallActivityMetricsResponse calls) {}
