package com.flowcrm.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description =
                "created/open/completed/cancelled are the created-at cohort with current TaskStatus. "
                        + "overdueNow is OPEN tasks past dueAt right now in the same user scope (not limited to the cohort).")
public record TaskActivityMetricsResponse(
        long created, long open, long completed, long cancelled, long overdueNow) {}
