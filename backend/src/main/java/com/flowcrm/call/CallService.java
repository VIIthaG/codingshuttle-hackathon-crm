package com.flowcrm.call;

import com.flowcrm.call.dto.CallCreateRequest;
import com.flowcrm.call.dto.CallResponse;
import com.flowcrm.call.dto.CallStatusUpdateRequest;
import com.flowcrm.call.dto.CallUpdateRequest;
import com.flowcrm.common.exception.ForbiddenException;
import com.flowcrm.common.exception.InvalidStatusTransitionException;
import com.flowcrm.common.exception.ResourceNotFoundException;
import com.flowcrm.crm.RelatedRecordBinder;
import com.flowcrm.crm.RelatedRecordViews;
import com.flowcrm.enums.CallDirection;
import com.flowcrm.enums.CallStatus;
import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.enums.Role;
import com.flowcrm.enums.SearchResultType;
import com.flowcrm.idempotency.IdempotencyOperations;
import com.flowcrm.idempotency.IdempotencyService;
import com.flowcrm.notification.NotificationService;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.user.User;
import com.flowcrm.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallService {

    private final CallRepository callRepository;
    private final UserRepository userRepository;
    private final RelatedRecordBinder relatedRecordBinder;
    private final IdempotencyService idempotencyService;
    private final NotificationService notificationService;
    private final CallService self;

    public CallService(
            CallRepository callRepository,
            UserRepository userRepository,
            RelatedRecordBinder relatedRecordBinder,
            IdempotencyService idempotencyService,
            NotificationService notificationService,
            @Lazy CallService self) {
        this.callRepository = callRepository;
        this.userRepository = userRepository;
        this.relatedRecordBinder = relatedRecordBinder;
        this.idempotencyService = idempotencyService;
        this.notificationService = notificationService;
        this.self = self;
    }

    @Transactional
    public CallResponse create(CallCreateRequest request, UserPrincipal principal) {
        User assignee = resolveAssignee(request.assignedToId(), principal);
        Call call = new Call();
        relatedRecordBinder.bind(
                call, request.leadId(), request.accountId(), request.contactId(), request.dealId(), principal);
        call.setAssignedTo(assignee);
        call.setTitle(request.title().trim());
        call.setDescription(trimToNull(request.description()));
        call.setScheduledAt(request.scheduledAt());
        call.setDurationMinutes(request.durationMinutes());
        call.setDirection(request.direction());
        call.setPhoneNumber(trimToNull(request.phoneNumber()));
        call.setOutcome(trimToNull(request.outcome()));
        call.setStatus(CallStatus.PLANNED);
        CallResponse response = toResponse(callRepository.save(call));
        notificationService.notifyAssignment(
                principal.getId(), assignee, null, SearchResultType.CALL, response.id(), response.title());
        return response;
    }

    public CallResponse create(CallCreateRequest request, UserPrincipal principal, String idempotencyKey) {
        return idempotencyService.execute(
                principal.getId(),
                IdempotencyOperations.CALLS_CREATE,
                idempotencyKey,
                request,
                CallResponse.class,
                HttpStatus.CREATED.value(),
                () -> self.create(request, principal));
    }

    @Transactional(readOnly = true)
    public Page<CallResponse> list(
            CallStatus status,
            CallDirection direction,
            UUID leadId,
            UUID accountId,
            UUID contactId,
            UUID dealId,
            RelatedRecordType relatedType,
            UUID assignedToId,
            Instant scheduledFrom,
            Instant scheduledTo,
            UserPrincipal principal,
            Pageable pageable) {
        Specification<Call> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (principal.getRole() != Role.ADMIN) {
                predicates.add(cb.equal(root.get("assignedTo").get("id"), principal.getId()));
            } else if (assignedToId != null) {
                predicates.add(cb.equal(root.get("assignedTo").get("id"), assignedToId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (direction != null) {
                predicates.add(cb.equal(root.get("direction"), direction));
            }
            if (leadId != null) {
                predicates.add(cb.equal(root.get("lead").get("id"), leadId));
            }
            if (accountId != null) {
                predicates.add(cb.equal(root.get("account").get("id"), accountId));
            }
            if (contactId != null) {
                predicates.add(cb.equal(root.get("contact").get("id"), contactId));
            }
            if (dealId != null) {
                predicates.add(cb.equal(root.get("deal").get("id"), dealId));
            }
            if (relatedType != null) {
                switch (relatedType) {
                    case LEAD -> predicates.add(cb.isNotNull(root.get("lead")));
                    case ACCOUNT -> predicates.add(cb.isNotNull(root.get("account")));
                    case CONTACT -> predicates.add(cb.isNotNull(root.get("contact")));
                    case DEAL -> predicates.add(cb.isNotNull(root.get("deal")));
                }
            }
            if (scheduledFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), scheduledFrom));
            }
            if (scheduledTo != null) {
                predicates.add(cb.lessThan(root.get("scheduledAt"), scheduledTo));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return callRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CallResponse getById(UUID id, UserPrincipal principal) {
        Call call = requireCall(id);
        assertCanAccess(call, principal);
        return toResponse(call);
    }

    @Transactional
    public CallResponse update(UUID id, CallUpdateRequest request, UserPrincipal principal) {
        Call call = requireCall(id);
        assertCanAccess(call, principal);
        UUID previousAssigneeId = call.getAssignedTo().getId();
        User assignee = resolveAssignee(request.assignedToId(), principal);
        relatedRecordBinder.bind(
                call, request.leadId(), request.accountId(), request.contactId(), request.dealId(), principal);
        call.setAssignedTo(assignee);
        call.setTitle(request.title().trim());
        call.setDescription(trimToNull(request.description()));
        call.setScheduledAt(request.scheduledAt());
        call.setDurationMinutes(request.durationMinutes());
        call.setDirection(request.direction());
        call.setPhoneNumber(trimToNull(request.phoneNumber()));
        call.setOutcome(trimToNull(request.outcome()));
        CallResponse response = toResponse(callRepository.save(call));
        notificationService.notifyAssignment(
                principal.getId(),
                assignee,
                previousAssigneeId,
                SearchResultType.CALL,
                response.id(),
                response.title());
        return response;
    }

    @Transactional
    public CallResponse changeStatus(UUID id, CallStatusUpdateRequest request, UserPrincipal principal) {
        Call call = requireCall(id);
        assertCanAccess(call, principal);
        CallStatus from = call.getStatus();
        CallStatus to = request.status();
        if (!CallStatusTransitions.canTransition(from, to)) {
            throw new InvalidStatusTransitionException("Cannot change call status from " + from + " to " + to);
        }
        call.setStatus(to);
        if (to == CallStatus.COMPLETED && request.outcome() != null) {
            call.setOutcome(trimToNull(request.outcome()));
        }
        return toResponse(callRepository.save(call));
    }

    @Transactional
    public void delete(UUID id, UserPrincipal principal) {
        Call call = requireCall(id);
        assertCanAccess(call, principal);
        callRepository.delete(call);
    }

    private User resolveAssignee(UUID assignedToId, UserPrincipal principal) {
        if (assignedToId == null) {
            return requireUser(principal.getId());
        }
        if (!assignedToId.equals(principal.getId()) && principal.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only admins can assign calls to other users");
        }
        return requireUser(assignedToId);
    }

    private void assertCanAccess(Call call, UserPrincipal principal) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (!call.getAssignedTo().getId().equals(principal.getId())) {
            throw new ForbiddenException("You do not have access to this call");
        }
    }

    private Call requireCall(UUID id) {
        return callRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Call not found: " + id));
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private CallResponse toResponse(Call call) {
        RelatedRecordViews.Snapshot rel = RelatedRecordViews.of(call);
        return new CallResponse(
                call.getId(),
                rel.type(),
                rel.relatedId(),
                rel.relatedName(),
                rel.leadId(),
                rel.leadName(),
                rel.accountId(),
                rel.accountName(),
                rel.contactId(),
                rel.contactName(),
                rel.dealId(),
                rel.dealName(),
                call.getAssignedTo().getId(),
                call.getAssignedTo().getFullName(),
                call.getTitle(),
                call.getDescription(),
                call.getScheduledAt(),
                call.getDurationMinutes(),
                call.getDirection(),
                call.getStatus(),
                call.getPhoneNumber(),
                call.getOutcome(),
                call.getCreatedAt(),
                call.getUpdatedAt());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
