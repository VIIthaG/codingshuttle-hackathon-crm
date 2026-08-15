package com.flowcrm.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Created-at activity mix for one period")
public record ActivityTrendPointResponse(String period, long tasks, long meetings, long calls) {}
