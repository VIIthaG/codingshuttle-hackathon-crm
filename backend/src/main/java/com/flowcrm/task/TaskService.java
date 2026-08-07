package com.flowcrm.task;

import com.flowcrm.common.exception.ForbiddenException;
import com.flowcrm.common.exception.ResourceNotFoundException;
import com.flowcrm.enums.Role;
import com.flowcrm.enums.TaskStatus;
import com.flowcrm.lead.Lead;
import com.flowcrm.lead.LeadService;
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

    public TaskService(TaskRepository taskRepository, UserRepository userRepository, LeadService leadService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.leadService = leadService;
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

        return toResponse(taskRepository.save(task));
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

        Lead lead = leadService.requireAccessibleLead(request.leadId(), principal);
        User assignee = resolveAssignee(request.assignedToId(), principal);

        task.setLead(lead);
        task.setAssignedTo(assignee);
        task.setTitle(request.title().trim());
        task.setDescription(trimToNull(request.description()));
        task.setDueAt(request.dueAt());
        task.setReminderAt(request.reminderAt());
        task.setStatus(request.status());

        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse complete(UUID id, UserPrincipal principal) {
        Task task = requireTask(id);
        assertCanAccessTask(task, principal);
        task.setStatus(TaskStatus.COMPLETED);
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void delete(UUID id, UserPrincipal principal) {
        Task task = requireTask(id);
        assertCanAccessTask(task, principal);
        taskRepository.delete(task);
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
