package com.flowcrm.task;

import com.flowcrm.enums.TaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    long countByStatus(TaskStatus status);

    long countByAssignedToIdAndStatus(UUID assignedToId, TaskStatus status);

    long countByStatusAndDueAtBefore(TaskStatus status, Instant dueBefore);

    long countByAssignedToIdAndStatusAndDueAtBefore(UUID assignedToId, TaskStatus status, Instant dueBefore);

    boolean existsByLead_Id(UUID leadId);

    boolean existsByAccount_Id(UUID accountId);

    boolean existsByContact_Id(UUID contactId);

    boolean existsByDeal_Id(UUID dealId);

    List<Task> findByLead_IdOrderByCreatedAtDesc(UUID leadId);

    List<Task> findByAccount_IdOrderByCreatedAtDesc(UUID accountId);

    List<Task> findByContact_IdOrderByCreatedAtDesc(UUID contactId);

    List<Task> findByDeal_IdOrderByCreatedAtDesc(UUID dealId);

    List<Task> findByStatusAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAsc(
            TaskStatus status, Instant from, Instant to);

    List<Task> findByAssignedTo_IdAndStatusAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAsc(
            UUID assignedToId, TaskStatus status, Instant from, Instant to);

    List<Task> findByStatusAndDueAtLessThanOrderByDueAtAsc(TaskStatus status, Instant dueBefore);

    List<Task> findByAssignedTo_IdAndStatusAndDueAtLessThanOrderByDueAtAsc(
            UUID assignedToId, TaskStatus status, Instant dueBefore);

    /**
     * OPEN tasks with reminderAt in [now, until] (inclusive window start exclusive end via query bounds).
     */
    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.status = :status
              AND t.reminderAt IS NOT NULL
              AND t.reminderAt >= :now
              AND t.reminderAt <= :until
            """)
    long countUpcomingFollowUps(
            @Param("status") TaskStatus status,
            @Param("now") Instant now,
            @Param("until") Instant until);

    @Query("""
            SELECT COUNT(t) FROM Task t
            WHERE t.assignedTo.id = :assignedToId
              AND t.status = :status
              AND t.reminderAt IS NOT NULL
              AND t.reminderAt >= :now
              AND t.reminderAt <= :until
            """)
    long countUpcomingFollowUpsForAssignee(
            @Param("assignedToId") UUID assignedToId,
            @Param("status") TaskStatus status,
            @Param("now") Instant now,
            @Param("until") Instant until);
}
