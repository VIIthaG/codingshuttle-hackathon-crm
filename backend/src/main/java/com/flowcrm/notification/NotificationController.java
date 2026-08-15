package com.flowcrm.notification;

import com.flowcrm.config.OpenApiConfig;
import com.flowcrm.notification.dto.NotificationResponse;
import com.flowcrm.notification.dto.UnreadCountResponse;
import com.flowcrm.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List current user's notifications", description = "Newest first. Never includes other users' inboxes.")
    public Page<NotificationResponse> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.list(unreadOnly, principal, pageable);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Unread notification count for the current user")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.unreadCount(principal);
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one of the current user's notifications read")
    public NotificationResponse markRead(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.markRead(id, principal);
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all of the current user's notifications read")
    public UnreadCountResponse markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.markAllRead(principal);
    }
}
