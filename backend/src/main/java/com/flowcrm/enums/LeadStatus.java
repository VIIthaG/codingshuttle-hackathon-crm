package com.flowcrm.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lead pipeline status")
public enum LeadStatus {
    NEW,
    CONTACTED,
    QUALIFIED,
    LOST,
    CONVERTED
}
