package com.flowcrm.search.dto;

import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.enums.SearchResultType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;

@Schema(description = "One role-scoped search hit")
public record SearchResultResponse(
        SearchResultType type,
        UUID id,
        String title,
        String subtitle,
        String status,
        RelatedRecordType relatedType,
        UUID relatedId,
        String relatedName,
        Map<String, Object> metadata) {}
