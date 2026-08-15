package com.flowcrm.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Bounded global search response")
public record SearchResponse(String query, List<SearchResultResponse> results) {}
