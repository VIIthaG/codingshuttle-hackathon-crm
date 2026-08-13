package com.flowcrm.task;

import com.flowcrm.common.exception.ErrorResponse;
import com.flowcrm.config.OpenApiConfig;
import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.enums.TaskStatus;
import com.flowcrm.idempotency.IdempotencyKeyValidator;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.task.dto.TaskCreateRequest;
import com.flowcrm.task.dto.TaskResponse;
import com.flowcrm.task.dto.TaskUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tasks")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create task",
            description =
                    "Creates a follow-up task related to exactly one Lead, Account, Contact, or Deal. "
                            + "When reminderAt is set, a FOLLOW_UP_SCHEDULED outbox event is recorded. "
                            + "Optionally send Idempotency-Key for durable create idempotency.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task created (or idempotent replay)"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed or blank Idempotency-Key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Related record not found / not accessible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Idempotency-Key reused with a different request body",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public TaskResponse create(
            @Valid @RequestBody TaskCreateRequest request,
            @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            required = false,
                            description =
                                    "Optional. Same key + same body replays the original 201 response. "
                                            + "Same key + different body returns 409.",
                            schema = @Schema(type = "string", maxLength = 255, example = "demo-task-001"))
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        String key = IdempotencyKeyValidator.normalizeOptional(idempotencyKey);
        if (key == null) {
            return taskService.create(request, principal);
        }
        return taskService.create(request, principal, key);
    }

    @GetMapping
    @Operation(
            summary = "List tasks",
            description =
                    "Paginated tasks with optional filters (status, leadId, accountId, contactId, dealId, relatedType, overdue). "
                            + "SALES_REP sees assigned tasks only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of tasks"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Page<TaskResponse> list(
            @Parameter(description = "Optional task status filter") @RequestParam(required = false) TaskStatus status,
            @Parameter(description = "Filter by lead id") @RequestParam(required = false) UUID leadId,
            @Parameter(description = "Filter by account id") @RequestParam(required = false) UUID accountId,
            @Parameter(description = "Filter by contact id") @RequestParam(required = false) UUID contactId,
            @Parameter(description = "Filter by deal id") @RequestParam(required = false) UUID dealId,
            @Parameter(description = "Filter by related record type") @RequestParam(required = false)
                    RelatedRecordType relatedType,
            @Parameter(description = "ADMIN only: filter by assignee") @RequestParam(required = false) UUID assignedToId,
            @Parameter(description = "When true, only OPEN tasks past dueAt") @RequestParam(required = false)
                    Boolean overdue,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.list(
                status, leadId, accountId, contactId, dealId, relatedType, assignedToId, overdue, principal, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task found"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public TaskResponse getById(
            @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.getById(id, principal);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update task")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task updated"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task or related record not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public TaskResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody TaskUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.update(id, request, principal);
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "Complete task", description = "Marks the task COMPLETED and supersedes pending follow-up schedules.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task completed"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public TaskResponse complete(
            @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.complete(id, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete task")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task deleted"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        taskService.delete(id, principal);
    }
}
