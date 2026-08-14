package com.flowcrm.meeting;

import com.flowcrm.common.exception.ForbiddenException;
import com.flowcrm.common.exception.InvalidStatusTransitionException;
import com.flowcrm.common.exception.ResourceNotFoundException;
import com.flowcrm.crm.RelatedRecordBinder;
import com.flowcrm.crm.RelatedRecordViews;
import com.flowcrm.enums.MeetingStatus;
import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.enums.Role;
import com.flowcrm.idempotency.IdempotencyOperations;
import com.flowcrm.idempotency.IdempotencyService;
import com.flowcrm.meeting.dto.MeetingCreateRequest;
import com.flowcrm.meeting.dto.MeetingResponse;
import com.flowcrm.meeting.dto.MeetingStatusUpdateRequest;
import com.flowcrm.meeting.dto.MeetingUpdateRequest;
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
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final RelatedRecordBinder relatedRecordBinder;
    private final IdempotencyService idempotencyService;
    private final MeetingService self;

    public MeetingService(
            MeetingRepository meetingRepository,
            UserRepository userRepository,
            RelatedRecordBinder relatedRecordBinder,
            IdempotencyService idempotencyService,
            @Lazy MeetingService self) {
        this.meetingRepository = meetingRepository;
        this.userRepository = userRepository;
        this.relatedRecordBinder = relatedRecordBinder;
        this.idempotencyService = idempotencyService;
        this.self = self;
    }

    @Transactional
    public MeetingResponse create(MeetingCreateRequest request, UserPrincipal principal) {
        User assignee = resolveAssignee(request.assignedToId(), principal);
        Meeting meeting = new Meeting();
        relatedRecordBinder.bind(
                meeting, request.leadId(), request.accountId(), request.contactId(), request.dealId(), principal);
        meeting.setAssignedTo(assignee);
        meeting.setTitle(request.title().trim());
        meeting.setDescription(trimToNull(request.description()));
        meeting.setStartAt(request.startAt());
        meeting.setEndAt(request.endAt());
        meeting.setLocation(trimToNull(request.location()));
        meeting.setMeetingUrl(trimToNull(request.meetingUrl()));
        meeting.setStatus(MeetingStatus.SCHEDULED);
        return toResponse(meetingRepository.save(meeting));
    }

    public MeetingResponse create(MeetingCreateRequest request, UserPrincipal principal, String idempotencyKey) {
        return idempotencyService.execute(
                principal.getId(),
                IdempotencyOperations.MEETINGS_CREATE,
                idempotencyKey,
                request,
                MeetingResponse.class,
                HttpStatus.CREATED.value(),
                () -> self.create(request, principal));
    }

    @Transactional(readOnly = true)
    public Page<MeetingResponse> list(
            MeetingStatus status,
            UUID leadId,
            UUID accountId,
            UUID contactId,
            UUID dealId,
            RelatedRecordType relatedType,
            UUID assignedToId,
            Instant startFrom,
            Instant startTo,
            UserPrincipal principal,
            Pageable pageable) {
        Specification<Meeting> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (principal.getRole() != Role.ADMIN) {
                predicates.add(cb.equal(root.get("assignedTo").get("id"), principal.getId()));
            } else if (assignedToId != null) {
                predicates.add(cb.equal(root.get("assignedTo").get("id"), assignedToId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
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
            if (startFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startAt"), startFrom));
            }
            if (startTo != null) {
                predicates.add(cb.lessThan(root.get("startAt"), startTo));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return meetingRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public MeetingResponse getById(UUID id, UserPrincipal principal) {
        Meeting meeting = requireMeeting(id);
        assertCanAccess(meeting, principal);
        return toResponse(meeting);
    }

    @Transactional
    public MeetingResponse update(UUID id, MeetingUpdateRequest request, UserPrincipal principal) {
        Meeting meeting = requireMeeting(id);
        assertCanAccess(meeting, principal);
        User assignee = resolveAssignee(request.assignedToId(), principal);
        relatedRecordBinder.bind(
                meeting, request.leadId(), request.accountId(), request.contactId(), request.dealId(), principal);
        meeting.setAssignedTo(assignee);
        meeting.setTitle(request.title().trim());
        meeting.setDescription(trimToNull(request.description()));
        meeting.setStartAt(request.startAt());
        meeting.setEndAt(request.endAt());
        meeting.setLocation(trimToNull(request.location()));
        meeting.setMeetingUrl(trimToNull(request.meetingUrl()));
        return toResponse(meetingRepository.save(meeting));
    }

    @Transactional
    public MeetingResponse changeStatus(UUID id, MeetingStatusUpdateRequest request, UserPrincipal principal) {
        Meeting meeting = requireMeeting(id);
        assertCanAccess(meeting, principal);
        MeetingStatus from = meeting.getStatus();
        MeetingStatus to = request.status();
        if (!MeetingStatusTransitions.canTransition(from, to)) {
            throw new InvalidStatusTransitionException("Cannot change meeting status from " + from + " to " + to);
        }
        meeting.setStatus(to);
        return toResponse(meetingRepository.save(meeting));
    }

    @Transactional
    public void delete(UUID id, UserPrincipal principal) {
        Meeting meeting = requireMeeting(id);
        assertCanAccess(meeting, principal);
        meetingRepository.delete(meeting);
    }

    private User resolveAssignee(UUID assignedToId, UserPrincipal principal) {
        if (assignedToId == null) {
            return requireUser(principal.getId());
        }
        if (!assignedToId.equals(principal.getId()) && principal.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only admins can assign meetings to other users");
        }
        return requireUser(assignedToId);
    }

    private void assertCanAccess(Meeting meeting, UserPrincipal principal) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (!meeting.getAssignedTo().getId().equals(principal.getId())) {
            throw new ForbiddenException("You do not have access to this meeting");
        }
    }

    private Meeting requireMeeting(UUID id) {
        return meetingRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found: " + id));
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private MeetingResponse toResponse(Meeting meeting) {
        RelatedRecordViews.Snapshot rel = RelatedRecordViews.of(meeting);
        return new MeetingResponse(
                meeting.getId(),
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
                meeting.getAssignedTo().getId(),
                meeting.getAssignedTo().getFullName(),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getStartAt(),
                meeting.getEndAt(),
                meeting.getLocation(),
                meeting.getMeetingUrl(),
                meeting.getStatus(),
                meeting.getCreatedAt(),
                meeting.getUpdatedAt());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
