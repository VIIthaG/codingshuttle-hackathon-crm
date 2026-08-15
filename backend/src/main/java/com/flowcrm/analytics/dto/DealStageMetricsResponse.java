package com.flowcrm.analytics.dto;

import com.flowcrm.enums.DealStage;
import java.math.BigDecimal;

public record DealStageMetricsResponse(DealStage stage, long count, BigDecimal totalAmount) {}
