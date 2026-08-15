package com.flowcrm.notification.dto;

import com.flowcrm.enums.NotificationType;
import com.flowcrm.enums.SearchResultType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "In-app notification for the authenticated user only")
public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String message,
        SearchResultType relatedEntityType,
        UUID relatedEntityId,
        Instant readAt,
        Instant createdAt) {}
