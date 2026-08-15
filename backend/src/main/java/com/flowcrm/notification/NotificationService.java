package com.flowcrm.notification;

import com.flowcrm.common.exception.ResourceNotFoundException;
import com.flowcrm.enums.NotificationType;
import com.flowcrm.enums.SearchResultType;
import com.flowcrm.notification.dto.NotificationResponse;
import com.flowcrm.notification.dto.UnreadCountResponse;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.user.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Writes an assignment notification in the caller's transaction.
     * Skips self-assignment and no-op reassignment (same owner).
     */
    public void notifyAssignment(
            UUID actorUserId,
            User newAssignee,
            UUID previousAssigneeId,
            SearchResultType entityType,
            UUID entityId,
            String recordTitle) {
        if (newAssignee == null || actorUserId == null) {
            return;
        }
        if (newAssignee.getId().equals(actorUserId)) {
            return;
        }
        if (previousAssigneeId != null && previousAssigneeId.equals(newAssignee.getId())) {
            return;
        }
        Notification notification = new Notification();
        notification.setUser(newAssignee);
        notification.setType(NotificationType.ASSIGNMENT);
        notification.setTitle(label(entityType) + " assigned to you");
        notification.setMessage(recordTitle);
        notification.setRelatedEntityType(entityType);
        notification.setRelatedEntityId(entityId);
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(boolean unreadOnly, UserPrincipal principal, Pageable pageable) {
        UUID userId = principal.getId();
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByUser_IdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(UserPrincipal principal) {
        return new UnreadCountResponse(notificationRepository.countByUser_IdAndReadAtIsNull(principal.getId()));
    }

    @Transactional
    public NotificationResponse markRead(UUID id, UserPrincipal principal) {
        Notification notification = notificationRepository
                .findByIdAndUser_Id(id, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    @Transactional
    public UnreadCountResponse markAllRead(UserPrincipal principal) {
        notificationRepository.markAllRead(principal.getId(), Instant.now());
        return unreadCount(principal);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getRelatedEntityType(),
                notification.getRelatedEntityId(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }

    private static String label(SearchResultType type) {
        return switch (type) {
            case LEAD -> "Lead";
            case ACCOUNT -> "Account";
            case CONTACT -> "Contact";
            case DEAL -> "Deal";
            case TASK -> "Task";
            case MEETING -> "Meeting";
            case CALL -> "Call";
        };
    }
}
