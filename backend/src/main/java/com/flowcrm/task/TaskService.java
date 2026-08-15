package com.flowcrm.task;

import com.flowcrm.account.Account;
import com.flowcrm.account.AccountService;
import com.flowcrm.common.exception.BadRequestException;
import com.flowcrm.common.exception.ForbiddenException;
import com.flowcrm.common.exception.ResourceNotFoundException;
import com.flowcrm.contact.Contact;
import com.flowcrm.contact.ContactService;
import com.flowcrm.dashboard.DashboardService;
import com.flowcrm.deal.Deal;
import com.flowcrm.deal.DealService;
import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.enums.Role;
import com.flowcrm.enums.SearchResultType;
import com.flowcrm.enums.TaskStatus;
import com.flowcrm.idempotency.IdempotencyOperations;
import com.flowcrm.idempotency.IdempotencyService;
import com.flowcrm.lead.Lead;
import com.flowcrm.lead.LeadService;
import com.flowcrm.notification.NotificationService;
import com.flowcrm.outbox.OutboxEventRecorder;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.task.dto.TaskCreateRequest;
import com.flowcrm.task.dto.TaskResponse;
import com.flowcrm.task.dto.TaskUpdateRequest;
import com.flowcrm.user.User;
import com.flowcrm.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final LeadService leadService;
    private final AccountService accountService;
    private final ContactService contactService;
    private final DealService dealService;
    private final OutboxEventRecorder outboxEventRecorder;
    private final DashboardService dashboardService;
    private final IdempotencyService idempotencyService;
    private final NotificationService notificationService;
    private final TaskService self;

    public TaskService(
            TaskRepository taskRepository,
            UserRepository userRepository,
            LeadService leadService,
            AccountService accountService,
            ContactService contactService,
            DealService dealService,
            OutboxEventRecorder outboxEventRecorder,
            DashboardService dashboardService,
            IdempotencyService idempotencyService,
            NotificationService notificationService,
            @Lazy TaskService self) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.leadService = leadService;
        this.accountService = accountService;
        this.contactService = contactService;
        this.dealService = dealService;
        this.outboxEventRecorder = outboxEventRecorder;
        this.dashboardService = dashboardService;
        this.idempotencyService = idempotencyService;
        this.notificationService = notificationService;
        this.self = self;
    }

    @Transactional
    public TaskResponse create(TaskCreateRequest request, UserPrincipal principal) {
        User assignee = resolveAssignee(request.assignedToId(), principal);

        Task task = new Task();
        applyRelation(task, request.leadId(), request.accountId(), request.contactId(), request.dealId(), principal);
        task.setAssignedTo(assignee);
        task.setTitle(request.title().trim());
        task.setDescription(trimToNull(request.description()));
        task.setDueAt(request.dueAt());
        task.setReminderAt(request.reminderAt());
        task.setStatus(TaskStatus.OPEN);

        Task saved = taskRepository.save(task);
        if (saved.getReminderAt() != null) {
            outboxEventRecorder.recordFollowUpScheduled(saved);
        }
        dashboardService.invalidateAllSummaries();
        TaskResponse created = toResponse(saved);
        notificationService.notifyAssignment(
                principal.getId(), assignee, null, SearchResultType.TASK, created.id(), created.title());
        return created;
    }

    /**
     * Idempotent create when {@code idempotencyKey} is present (validated by the controller).
     */
    public TaskResponse create(TaskCreateRequest request, UserPrincipal principal, String idempotencyKey) {
        return idempotencyService.execute(
                principal.getId(),
                IdempotencyOperations.TASKS_CREATE,
                idempotencyKey,
                request,
                TaskResponse.class,
                HttpStatus.CREATED.value(),
                () -> self.create(request, principal));
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> list(
            TaskStatus status,
            UUID leadId,
            UUID accountId,
            UUID contactId,
            UUID dealId,
            RelatedRecordType relatedType,
            UUID assignedToId,
            Boolean overdue,
            UserPrincipal principal,
            Pageable pageable) {
        Specification<Task> spec =
                buildListSpec(status, leadId, accountId, contactId, dealId, relatedType, assignedToId, overdue, principal);
        return taskRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TaskResponse getById(UUID id, UserPrincipal principal) {
        Task task = requireTask(id);
        assertCanAccessTask(task, principal);
        return toResponse(task);
    }

    @Transactional
    public TaskResponse update(UUID id, TaskUpdateRequest request, UserPrincipal principal) {
        Task task = requireTask(id);
        assertCanAccessTask(task, principal);

        Instant previousReminderAt = task.getReminderAt();
        TaskStatus previousStatus = task.getStatus();
        UUID previousAssigneeId = task.getAssignedTo().getId();

        User assignee = resolveAssignee(request.assignedToId(), principal);

        applyRelation(task, request.leadId(), request.accountId(), request.contactId(), request.dealId(), principal);
        task.setAssignedTo(assignee);
        task.setTitle(request.title().trim());
        task.setDescription(trimToNull(request.description()));
        task.setDueAt(request.dueAt());
        task.setReminderAt(request.reminderAt());
        task.setStatus(request.status());

        Task saved = taskRepository.save(task);
        syncFollowUpOutbox(saved, previousReminderAt, previousStatus);
        dashboardService.invalidateAllSummaries();
        TaskResponse updated = toResponse(saved);
        notificationService.notifyAssignment(
                principal.getId(),
                assignee,
                previousAssigneeId,
                SearchResultType.TASK,
                updated.id(),
                updated.title());
        return updated;
    }

    @Transactional
    public TaskResponse complete(UUID id, UserPrincipal principal) {
        Task task = requireTask(id);
        assertCanAccessTask(task, principal);
        TaskStatus previousStatus = task.getStatus();
        Instant previousReminderAt = task.getReminderAt();
        task.setStatus(TaskStatus.COMPLETED);
        Task saved = taskRepository.save(task);
        syncFollowUpOutbox(saved, previousReminderAt, previousStatus);
        dashboardService.invalidateAllSummaries();
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id, UserPrincipal principal) {
        Task task = requireTask(id);
        assertCanAccessTask(task, principal);
        outboxEventRecorder.supersedePendingFollowUps(task.getId());
        taskRepository.delete(task);
        dashboardService.invalidateAllSummaries();
    }

    /**
     * Keeps PENDING FOLLOW_UP_SCHEDULED outbox rows aligned with the task.
     * Reschedule / clear / complete / cancel supersedes prior PENDING schedules;
     * a new PENDING schedule is written only when an OPEN task has a (new) reminderAt.
     */
    private void syncFollowUpOutbox(Task task, Instant previousReminderAt, TaskStatus previousStatus) {
        boolean reminderChanged = !Objects.equals(previousReminderAt, task.getReminderAt());
        boolean becameIneligible =
                isReminderEligible(previousStatus) && !isReminderEligible(task.getStatus());
        boolean needsSupersede = reminderChanged || becameIneligible;

        if (needsSupersede) {
            outboxEventRecorder.supersedePendingFollowUps(task.getId());
        }

        boolean shouldSchedule =
                isReminderEligible(task.getStatus())
                        && task.getReminderAt() != null
                        && reminderChanged;

        if (shouldSchedule) {
            outboxEventRecorder.recordFollowUpScheduled(task);
        }
    }

    private boolean isReminderEligible(TaskStatus status) {
        return status == TaskStatus.OPEN;
    }

    private void applyRelation(
            Task task, UUID leadId, UUID accountId, UUID contactId, UUID dealId, UserPrincipal principal) {
        int count = 0;
        if (leadId != null) {
            count++;
        }
        if (accountId != null) {
            count++;
        }
        if (contactId != null) {
            count++;
        }
        if (dealId != null) {
            count++;
        }
        if (count != 1) {
            throw new BadRequestException("Exactly one of leadId, accountId, contactId, or dealId is required");
        }

        task.clearRelations();
        if (leadId != null) {
            Lead lead = leadService.requireAccessibleLead(leadId, principal);
            task.setLead(lead);
        } else if (accountId != null) {
            Account account = accountService.requireAccessibleAccount(accountId, principal);
            task.setAccount(account);
        } else if (contactId != null) {
            Contact contact = contactService.requireAccessibleContact(contactId, principal);
            task.setContact(contact);
        } else {
            Deal deal = dealService.requireAccessibleDeal(dealId, principal);
            task.setDeal(deal);
        }
    }

    private Specification<Task> buildListSpec(
            TaskStatus status,
            UUID leadId,
            UUID accountId,
            UUID contactId,
            UUID dealId,
            RelatedRecordType relatedType,
            UUID assignedToId,
            Boolean overdue,
            UserPrincipal principal) {
        return (root, query, cb) -> {
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
            if (Boolean.TRUE.equals(overdue)) {
                predicates.add(cb.equal(root.get("status"), TaskStatus.OPEN));
                predicates.add(cb.lessThan(root.get("dueAt"), Instant.now()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private User resolveAssignee(UUID assignedToId, UserPrincipal principal) {
        if (assignedToId == null) {
            return requireUser(principal.getId());
        }
        if (!assignedToId.equals(principal.getId()) && principal.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only admins can assign tasks to other users");
        }
        return requireUser(assignedToId);
    }

    private void assertCanAccessTask(Task task, UserPrincipal principal) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (!task.getAssignedTo().getId().equals(principal.getId())) {
            throw new ForbiddenException("You do not have access to this task");
        }
    }

    private Task requireTask(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private TaskResponse toResponse(Task task) {
        RelatedRecordType type = task.relatedType();
        Lead lead = task.getLead();
        Account account = task.getAccount();
        Contact contact = task.getContact();
        Deal deal = task.getDeal();
        String contactName = contact == null ? null : (contact.getFirstName() + " " + contact.getLastName()).trim();
        return new TaskResponse(
                task.getId(),
                type,
                task.relatedId(),
                task.relatedName(),
                lead == null ? null : lead.getId(),
                lead == null ? null : lead.getFullName(),
                account == null ? null : account.getId(),
                account == null ? null : account.getName(),
                contact == null ? null : contact.getId(),
                contactName,
                deal == null ? null : deal.getId(),
                deal == null ? null : deal.getName(),
                task.getAssignedTo().getId(),
                task.getAssignedTo().getFullName(),
                task.getTitle(),
                task.getDescription(),
                task.getDueAt(),
                task.getReminderAt(),
                task.getStatus(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
