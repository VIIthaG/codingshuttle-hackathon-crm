package com.flowcrm.task;

import com.flowcrm.common.exception.ForbiddenException;
import com.flowcrm.common.exception.ResourceNotFoundException;
import com.flowcrm.dashboard.DashboardService;
import com.flowcrm.enums.Role;
import com.flowcrm.enums.TaskStatus;
import com.flowcrm.lead.Lead;
import com.flowcrm.lead.LeadService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final LeadService leadService;
    private final OutboxEventRecorder outboxEventRecorder;
    private final DashboardService dashboardService;

    public TaskService(
            TaskRepository taskRepository,
            UserRepository userRepository,
            LeadService leadService,
            OutboxEventRecorder outboxEventRecorder,
            DashboardService dashboardService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.leadService = leadService;
        this.outboxEventRecorder = outboxEventRecorder;
        this.dashboardService = dashboardService;
    }

    @Transactional
    public TaskResponse create(TaskCreateRequest request, UserPrincipal principal) {
        Lead lead = leadService.requireAccessibleLead(request.leadId(), principal);
        User assignee = resolveAssignee(request.assignedToId(), principal);

        Task task = new Task();
        task.setLead(lead);
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
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> list(
            TaskStatus status,
            UUID leadId,
            UUID assignedToId,
            Boolean overdue,
            UserPrincipal principal,
            Pageable pageable) {
        Specification<Task> spec = buildListSpec(status, leadId, assignedToId, overdue, principal);
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

        Lead lead = leadService.requireAccessibleLead(request.leadId(), principal);
        User assignee = resolveAssignee(request.assignedToId(), principal);

        task.setLead(lead);
        task.setAssignedTo(assignee);
        task.setTitle(request.title().trim());
        task.setDescription(trimToNull(request.description()));
        task.setDueAt(request.dueAt());
        task.setReminderAt(request.reminderAt());
        task.setStatus(request.status());

        Task saved = taskRepository.save(task);
        syncFollowUpOutbox(saved, previousReminderAt, previousStatus);
        dashboardService.invalidateAllSummaries();
        return toResponse(saved);
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

    private Specification<Task> buildListSpec(
            TaskStatus status,
            UUID leadId,
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
        return new TaskResponse(
                task.getId(),
                task.getLead().getId(),
                task.getLead().getFullName(),
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
