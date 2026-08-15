package com.flowcrm.analytics;

import com.flowcrm.enums.CallStatus;
import com.flowcrm.enums.DealStage;
import com.flowcrm.enums.LeadStatus;
import com.flowcrm.enums.MeetingStatus;
import com.flowcrm.enums.TaskStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsRepository {

    @PersistenceContext
    private EntityManager em;

    public long countLeads(UUID scopeId) {
        return singleLong(
                """
                SELECT COUNT(l) FROM Lead l
                WHERE (:scoped = false OR l.assignedTo.id = :scopeId)
                """,
                scopeId);
    }

    public long countLeadsCreated(UUID scopeId, Instant from, Instant toExclusive) {
        return singleLong(
                """
                SELECT COUNT(l) FROM Lead l
                WHERE l.createdAt >= :from AND l.createdAt < :toExclusive
                  AND (:scoped = false OR l.assignedTo.id = :scopeId)
                """,
                scopeId,
                from,
                toExclusive);
    }

    public List<Object[]> leadStatusCountsCreated(UUID scopeId, Instant from, Instant toExclusive) {
        return em.createQuery(
                        """
                        SELECT l.status, COUNT(l) FROM Lead l
                        WHERE l.createdAt >= :from AND l.createdAt < :toExclusive
                          AND (:scoped = false OR l.assignedTo.id = :scopeId)
                        GROUP BY l.status
                        """,
                        Object[].class)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .setParameter("from", from)
                .setParameter("toExclusive", toExclusive)
                .getResultList();
    }

    public List<Instant> leadCreatedAt(UUID scopeId, Instant from, Instant toExclusive) {
        return em.createQuery(
                        """
                        SELECT l.createdAt FROM Lead l
                        WHERE l.createdAt >= :from AND l.createdAt < :toExclusive
                          AND (:scoped = false OR l.assignedTo.id = :scopeId)
                        """,
                        Instant.class)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .setParameter("from", from)
                .setParameter("toExclusive", toExclusive)
                .getResultList();
    }

    public List<Instant> leadConvertedAt(UUID scopeId, Instant from, Instant toExclusive) {
        return em.createQuery(
                        """
                        SELECT l.convertedAt FROM Lead l
                        WHERE l.convertedAt IS NOT NULL
                          AND l.convertedAt >= :from AND l.convertedAt < :toExclusive
                          AND (:scoped = false OR l.assignedTo.id = :scopeId)
                        """,
                        Instant.class)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .setParameter("from", from)
                .setParameter("toExclusive", toExclusive)
                .getResultList();
    }

    public long countDeals(UUID scopeId) {
        return singleLong(
                """
                SELECT COUNT(d) FROM Deal d
                WHERE (:scoped = false OR d.owner.id = :scopeId)
                """,
                scopeId);
    }

    public long countDealsCreated(UUID scopeId, Instant from, Instant toExclusive) {
        return singleLong(
                """
                SELECT COUNT(d) FROM Deal d
                WHERE d.createdAt >= :from AND d.createdAt < :toExclusive
                  AND (:scoped = false OR d.owner.id = :scopeId)
                """,
                scopeId,
                from,
                toExclusive);
    }

    public long countDealsByStage(UUID scopeId, DealStage stage) {
        return em.createQuery(
                        """
                        SELECT COUNT(d) FROM Deal d
                        WHERE d.stage = :stage
                          AND (:scoped = false OR d.owner.id = :scopeId)
                        """,
                        Long.class)
                .setParameter("stage", stage)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .getSingleResult();
    }

    public BigDecimal sumDealAmountByStage(UUID scopeId, DealStage stage) {
        BigDecimal value = em.createQuery(
                        """
                        SELECT COALESCE(SUM(COALESCE(d.amount, 0)), 0) FROM Deal d
                        WHERE d.stage = :stage
                          AND (:scoped = false OR d.owner.id = :scopeId)
                        """,
                        BigDecimal.class)
                .setParameter("stage", stage)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .getSingleResult();
        return value == null ? BigDecimal.ZERO : value;
    }

    public long countOpenDeals(UUID scopeId) {
        return em.createQuery(
                        """
                        SELECT COUNT(d) FROM Deal d
                        WHERE d.stage NOT IN (:won, :lost)
                          AND (:scoped = false OR d.owner.id = :scopeId)
                        """,
                        Long.class)
                .setParameter("won", DealStage.CLOSED_WON)
                .setParameter("lost", DealStage.CLOSED_LOST)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .getSingleResult();
    }

    public BigDecimal sumOpenDealAmount(UUID scopeId) {
        BigDecimal value = em.createQuery(
                        """
                        SELECT COALESCE(SUM(COALESCE(d.amount, 0)), 0) FROM Deal d
                        WHERE d.stage NOT IN (:won, :lost)
                          AND (:scoped = false OR d.owner.id = :scopeId)
                        """,
                        BigDecimal.class)
                .setParameter("won", DealStage.CLOSED_WON)
                .setParameter("lost", DealStage.CLOSED_LOST)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .getSingleResult();
        return value == null ? BigDecimal.ZERO : value;
    }

    public BigDecimal sumOpenAmountTimesProbability(UUID scopeId) {
        BigDecimal value = em.createQuery(
                        """
                        SELECT COALESCE(SUM(COALESCE(d.amount, 0) * d.probability), 0) FROM Deal d
                        WHERE d.stage NOT IN (:won, :lost)
                          AND (:scoped = false OR d.owner.id = :scopeId)
                        """,
                        BigDecimal.class)
                .setParameter("won", DealStage.CLOSED_WON)
                .setParameter("lost", DealStage.CLOSED_LOST)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .getSingleResult();
        return value == null ? BigDecimal.ZERO : value;
    }

    public List<Object[]> dealStageSnapshot(UUID scopeId) {
        return em.createQuery(
                        """
                        SELECT d.stage, COUNT(d), COALESCE(SUM(COALESCE(d.amount, 0)), 0)
                        FROM Deal d
                        WHERE (:scoped = false OR d.owner.id = :scopeId)
                        GROUP BY d.stage
                        """,
                        Object[].class)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .getResultList();
    }

    public List<Instant> dealCreatedAt(UUID scopeId, Instant from, Instant toExclusive) {
        return em.createQuery(
                        """
                        SELECT d.createdAt FROM Deal d
                        WHERE d.createdAt >= :from AND d.createdAt < :toExclusive
                          AND (:scoped = false OR d.owner.id = :scopeId)
                        """,
                        Instant.class)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .setParameter("from", from)
                .setParameter("toExclusive", toExclusive)
                .getResultList();
    }

    public long countTasksCreated(UUID scopeId, Instant from, Instant toExclusive) {
        return singleLong(
                """
                SELECT COUNT(t) FROM Task t
                WHERE t.createdAt >= :from AND t.createdAt < :toExclusive
                  AND (:scoped = false OR t.assignedTo.id = :scopeId)
                """,
                scopeId,
                from,
                toExclusive);
    }

    public long countTasksCreatedWithStatus(UUID scopeId, Instant from, Instant toExclusive, TaskStatus status) {
        return em.createQuery(
                        """
                        SELECT COUNT(t) FROM Task t
                        WHERE t.createdAt >= :from AND t.createdAt < :toExclusive
                          AND t.status = :status
                          AND (:scoped = false OR t.assignedTo.id = :scopeId)
                        """,
                        Long.class)
                .setParameter("from", from)
                .setParameter("toExclusive", toExclusive)
                .setParameter("status", status)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .getSingleResult();
    }

    public long countOverdueOpenTasks(UUID scopeId, Instant now) {
        return em.createQuery(
                        """
                        SELECT COUNT(t) FROM Task t
                        WHERE t.status = :status AND t.dueAt < :now
                          AND (:scoped = false OR t.assignedTo.id = :scopeId)
                        """,
                        Long.class)
                .setParameter("status", TaskStatus.OPEN)
                .setParameter("now", now)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .getSingleResult();
    }

    public List<Instant> taskCreatedAt(UUID scopeId, Instant from, Instant toExclusive) {
        return timestamps("SELECT t.createdAt FROM Task t WHERE t.createdAt >= :from AND t.createdAt < :toExclusive AND (:scoped = false OR t.assignedTo.id = :scopeId)", scopeId, from, toExclusive);
    }

    public long countMeetingsCreated(UUID scopeId, Instant from, Instant toExclusive) {
        return singleLong(
                """
                SELECT COUNT(m) FROM Meeting m
                WHERE m.createdAt >= :from AND m.createdAt < :toExclusive
                  AND (:scoped = false OR m.assignedTo.id = :scopeId)
                """,
                scopeId,
                from,
                toExclusive);
    }

    public long countMeetingsCreatedWithStatus(
            UUID scopeId, Instant from, Instant toExclusive, MeetingStatus status) {
        return em.createQuery(
                        """
                        SELECT COUNT(m) FROM Meeting m
                        WHERE m.createdAt >= :from AND m.createdAt < :toExclusive
                          AND m.status = :status
                          AND (:scoped = false OR m.assignedTo.id = :scopeId)
                        """,
                        Long.class)
                .setParameter("from", from)
                .setParameter("toExclusive", toExclusive)
                .setParameter("status", status)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .getSingleResult();
    }

    public List<Instant> meetingCreatedAt(UUID scopeId, Instant from, Instant toExclusive) {
        return timestamps("SELECT m.createdAt FROM Meeting m WHERE m.createdAt >= :from AND m.createdAt < :toExclusive AND (:scoped = false OR m.assignedTo.id = :scopeId)", scopeId, from, toExclusive);
    }

    public long countCallsCreated(UUID scopeId, Instant from, Instant toExclusive) {
        return singleLong(
                """
                SELECT COUNT(c) FROM Call c
                WHERE c.createdAt >= :from AND c.createdAt < :toExclusive
                  AND (:scoped = false OR c.assignedTo.id = :scopeId)
                """,
                scopeId,
                from,
                toExclusive);
    }

    public long countCallsCreatedWithStatus(UUID scopeId, Instant from, Instant toExclusive, CallStatus status) {
        return em.createQuery(
                        """
                        SELECT COUNT(c) FROM Call c
                        WHERE c.createdAt >= :from AND c.createdAt < :toExclusive
                          AND c.status = :status
                          AND (:scoped = false OR c.assignedTo.id = :scopeId)
                        """,
                        Long.class)
                .setParameter("from", from)
                .setParameter("toExclusive", toExclusive)
                .setParameter("status", status)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .getSingleResult();
    }

    public List<Instant> callCreatedAt(UUID scopeId, Instant from, Instant toExclusive) {
        return timestamps("SELECT c.createdAt FROM Call c WHERE c.createdAt >= :from AND c.createdAt < :toExclusive AND (:scoped = false OR c.assignedTo.id = :scopeId)", scopeId, from, toExclusive);
    }

    public List<Object[]> openDealsByOwner() {
        return em.createQuery(
                        """
                        SELECT d.owner.id, COUNT(d), COALESCE(SUM(COALESCE(d.amount, 0)), 0)
                        FROM Deal d
                        WHERE d.stage NOT IN (:won, :lost)
                        GROUP BY d.owner.id
                        """,
                        Object[].class)
                .setParameter("won", DealStage.CLOSED_WON)
                .setParameter("lost", DealStage.CLOSED_LOST)
                .getResultList();
    }

    public List<Object[]> wonDealsByOwner() {
        return em.createQuery(
                        """
                        SELECT d.owner.id, COUNT(d), COALESCE(SUM(COALESCE(d.amount, 0)), 0)
                        FROM Deal d
                        WHERE d.stage = :won
                        GROUP BY d.owner.id
                        """,
                        Object[].class)
                .setParameter("won", DealStage.CLOSED_WON)
                .getResultList();
    }

    public List<Object[]> openTasksByAssignee() {
        return em.createQuery(
                        """
                        SELECT t.assignedTo.id, COUNT(t) FROM Task t
                        WHERE t.status = :status
                        GROUP BY t.assignedTo.id
                        """,
                        Object[].class)
                .setParameter("status", TaskStatus.OPEN)
                .getResultList();
    }

    public List<Object[]> overdueTasksByAssignee(Instant now) {
        return em.createQuery(
                        """
                        SELECT t.assignedTo.id, COUNT(t) FROM Task t
                        WHERE t.status = :status AND t.dueAt < :now
                        GROUP BY t.assignedTo.id
                        """,
                        Object[].class)
                .setParameter("status", TaskStatus.OPEN)
                .setParameter("now", now)
                .getResultList();
    }

    public List<Object[]> scheduledMeetingsByAssignee() {
        return em.createQuery(
                        """
                        SELECT m.assignedTo.id, COUNT(m) FROM Meeting m
                        WHERE m.status = :status
                        GROUP BY m.assignedTo.id
                        """,
                        Object[].class)
                .setParameter("status", MeetingStatus.SCHEDULED)
                .getResultList();
    }

    public List<Object[]> plannedCallsByAssignee() {
        return em.createQuery(
                        """
                        SELECT c.assignedTo.id, COUNT(c) FROM Call c
                        WHERE c.status = :status
                        GROUP BY c.assignedTo.id
                        """,
                        Object[].class)
                .setParameter("status", CallStatus.PLANNED)
                .getResultList();
    }

    private long singleLong(String jpql, UUID scopeId) {
        return em.createQuery(jpql, Long.class)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .getSingleResult();
    }

    private long singleLong(String jpql, UUID scopeId, Instant from, Instant toExclusive) {
        return em.createQuery(jpql, Long.class)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .setParameter("from", from)
                .setParameter("toExclusive", toExclusive)
                .getSingleResult();
    }

    private List<Instant> timestamps(String jpql, UUID scopeId, Instant from, Instant toExclusive) {
        return em.createQuery(jpql, Instant.class)
                .setParameter("scoped", scopeId != null)
                .setParameter("scopeId", scopeId)
                .setParameter("from", from)
                .setParameter("toExclusive", toExclusive)
                .getResultList();
    }
}
