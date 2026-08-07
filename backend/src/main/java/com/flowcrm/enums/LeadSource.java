package com.flowcrm.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lead acquisition source")
public enum LeadSource {
    WEB,
    REFERRAL,
    COLD_CALL,
    EVENT,
    OTHER
}
