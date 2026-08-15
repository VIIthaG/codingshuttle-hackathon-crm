package com.flowcrm.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
        description =
                "Created-at series (and converted_at for conversions). Missing DAY/MONTH buckets are filled with zeros "
                        + "inside the selected window. There is no historical stage-transition series.")
public record AnalyticsTrendsResponse(
        List<CountPointResponse> leads,
        List<CountPointResponse> conversions,
        List<CountPointResponse> deals,
        List<ActivityTrendPointResponse> activities) {}
