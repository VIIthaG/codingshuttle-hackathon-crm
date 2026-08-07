package com.flowcrm.dashboard.dto;

import com.flowcrm.enums.LeadStatus;
import java.util.LinkedHashMap;
import java.util.Map;

public record DashboardSummaryResponse(
        long totalLeads,
        Map<LeadStatus, Long> leadsByStatus,
        long openTasks,
        long overdueTasks,
        long upcomingFollowUps) {

    public static DashboardSummaryResponse of(
            long totalLeads,
            Map<LeadStatus, Long> leadsByStatus,
            long openTasks,
            long overdueTasks,
            long upcomingFollowUps) {
        // LinkedHashMap (not EnumMap): safer Redis/HTTP JSON round-trips for enum-keyed maps.
        Map<LeadStatus, Long> byStatus = new LinkedHashMap<>();
        for (LeadStatus status : LeadStatus.values()) {
            byStatus.put(status, leadsByStatus.getOrDefault(status, 0L));
        }
        return new DashboardSummaryResponse(totalLeads, byStatus, openTasks, overdueTasks, upcomingFollowUps);
    }
}
