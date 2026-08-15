package com.flowcrm.analytics.dto;

import com.flowcrm.enums.LeadStatus;

public record LeadStatusCountResponse(LeadStatus status, long count) {}
