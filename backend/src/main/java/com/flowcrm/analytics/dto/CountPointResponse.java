package com.flowcrm.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Time-series point. period is yyyy-MM-dd for DAY buckets or yyyy-MM for MONTH buckets")
public record CountPointResponse(String period, long count) {}
