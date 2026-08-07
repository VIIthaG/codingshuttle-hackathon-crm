package com.flowcrm.dashboard;

import com.flowcrm.config.CacheConfig;
import com.flowcrm.dashboard.dto.DashboardSummaryResponse;
import com.flowcrm.enums.LeadStatus;
import com.flowcrm.enums.Role;
import com.flowcrm.enums.TaskStatus;
import com.flowcrm.lead.LeadRepository;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.task.TaskRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final LeadRepository leadRepository;
    private final TaskRepository taskRepository;

    public DashboardService(LeadRepository leadRepository, TaskRepository taskRepository) {
        this.leadRepository = leadRepository;
        this.taskRepository = taskRepository;
    }

    /**
     * Cached per authenticated user (ADMIN vs SALES_REP scopes differ by userId).
     * Cache key: dashboard-summary::&lt;userId&gt;
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.DASHBOARD_SUMMARY_CACHE, key = "#principal.id")
    public DashboardSummaryResponse getSummary(UserPrincipal principal) {
        log.info("Dashboard cache MISS userId={} role={}", principal.getId(), principal.getRole());
        Instant now = Instant.now();
        Instant upcomingUntil = now.plus(7, ChronoUnit.DAYS);

        if (principal.getRole() == Role.ADMIN) {
            return buildAdminSummary(now, upcomingUntil);
        }
        return buildRepSummary(principal.getId(), now, upcomingUntil);
    }

    /**
     * Hackathon-simple invalidation: clear all dashboard-summary entries after any
     * lead/task mutation. Per-user eviction would miss ADMIN aggregates when a
     * SALES_REP's data changes (and vice versa for reassignment).
     */
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD_SUMMARY_CACHE, allEntries = true)
    public void invalidateAllSummaries() {
        log.info("Dashboard cache INVALIDATE allEntries=true");
    }

    private DashboardSummaryResponse buildAdminSummary(Instant now, Instant upcomingUntil) {
        long totalLeads = leadRepository.count();
        Map<LeadStatus, Long> byStatus = toStatusMap(leadRepository.countGroupedByStatus());
        long openTasks = taskRepository.countByStatus(TaskStatus.OPEN);
        long overdueTasks = taskRepository.countByStatusAndDueAtBefore(TaskStatus.OPEN, now);
        long upcomingFollowUps = taskRepository.countUpcomingFollowUps(TaskStatus.OPEN, now, upcomingUntil);
        return DashboardSummaryResponse.of(totalLeads, byStatus, openTasks, overdueTasks, upcomingFollowUps);
    }

    private DashboardSummaryResponse buildRepSummary(UUID userId, Instant now, Instant upcomingUntil) {
        long totalLeads = leadRepository.countByAssignedToId(userId);
        Map<LeadStatus, Long> byStatus = toStatusMap(leadRepository.countGroupedByStatusForAssignee(userId));
        long openTasks = taskRepository.countByAssignedToIdAndStatus(userId, TaskStatus.OPEN);
        long overdueTasks = taskRepository.countByAssignedToIdAndStatusAndDueAtBefore(userId, TaskStatus.OPEN, now);
        long upcomingFollowUps =
                taskRepository.countUpcomingFollowUpsForAssignee(userId, TaskStatus.OPEN, now, upcomingUntil);
        return DashboardSummaryResponse.of(totalLeads, byStatus, openTasks, overdueTasks, upcomingFollowUps);
    }

    private Map<LeadStatus, Long> toStatusMap(List<Object[]> rows) {
        Map<LeadStatus, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put((LeadStatus) row[0], (Long) row[1]);
        }
        return map;
    }
}
