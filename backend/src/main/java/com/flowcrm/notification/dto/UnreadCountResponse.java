package com.flowcrm.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Unread inbox count for the current user")
public record UnreadCountResponse(long count) {}
