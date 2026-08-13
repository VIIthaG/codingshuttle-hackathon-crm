package com.flowcrm.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "CRM record type a task or activity item relates to")
public enum RelatedRecordType {
    LEAD,
    ACCOUNT,
    CONTACT,
    DEAL
}
