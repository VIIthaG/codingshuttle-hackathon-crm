package com.flowcrm.task;

import com.flowcrm.enums.TaskStatus;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.task.dto.TaskCreateRequest;
import com.flowcrm.task.dto.TaskResponse;
import com.flowcrm.task.dto.TaskUpdateRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(
            @Valid @RequestBody TaskCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.create(request, principal);
    }

    @GetMapping
    public Page<TaskResponse> list(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) UUID assignedToId,
            @RequestParam(required = false) Boolean overdue,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.list(status, leadId, assignedToId, overdue, principal, pageable);
    }

    @GetMapping("/{id}")
    public TaskResponse getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.getById(id, principal);
    }

    @PutMapping("/{id}")
    public TaskResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody TaskUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.update(id, request, principal);
    }

    @PatchMapping("/{id}/complete")
    public TaskResponse complete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.complete(id, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        taskService.delete(id, principal);
    }
}
