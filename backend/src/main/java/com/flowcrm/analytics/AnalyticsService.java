package com.flowcrm.analytics;

import com.flowcrm.analytics.dto.ActivityAnalyticsResponse;
import com.flowcrm.analytics.dto.ActivityTrendPointResponse;
import com.flowcrm.analytics.dto.AnalyticsRangeResponse;
import com.flowcrm.analytics.dto.AnalyticsSummaryResponse;
import com.flowcrm.analytics.dto.AnalyticsTrendsResponse;
import com.flowcrm.analytics.dto.CallActivityMetricsResponse;
import com.flowcrm.analytics.dto.CountPointResponse;
import com.flowcrm.analytics.dto.DealAnalyticsResponse;
import com.flowcrm.analytics.dto.DealStageMetricsResponse;
import com.flowcrm.analytics.dto.LeadAnalyticsResponse;
import com.flowcrm.analytics.dto.LeadStatusCountResponse;
import com.flowcrm.analytics.dto.MeetingActivityMetricsResponse;
import com.flowcrm.analytics.dto.TaskActivityMetricsResponse;
import com.flowcrm.analytics.dto.TeamMemberMetricsResponse;
import com.flowcrm.analytics.dto.TrendBucket;
import com.flowcrm.common.exception.ForbiddenException;
import com.flowcrm.common.exception.ResourceNotFoundException;
import com.flowcrm.enums.CallStatus;
import com.flowcrm.enums.DealStage;
import com.flowcrm.enums.LeadStatus;
import com.flowcrm.enums.MeetingStatus;
import com.flowcrm.enums.Role;
import com.flowcrm.enums.TaskStatus;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.user.User;
import com.flowcrm.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AnalyticsRangeResolver rangeResolver;
    private final AnalyticsRepository analyticsRepository;
    private final UserRepository userRepository;

    public AnalyticsService(
            AnalyticsRangeResolver rangeResolver,
            AnalyticsRepository analyticsRepository,
            UserRepository userRepository) {
        this.rangeResolver = rangeResolver;
        this.analyticsRepository = analyticsRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(
            String range, Instant from, Instant toExclusive, UUID assignedTo, UserPrincipal principal) {
        UUID scopeId = resolveScope(assignedTo, principal);
        AnalyticsRangeResponse window = rangeResolver.resolve(range, from, toExclusive);
        Instant now = Instant.now();

        LeadAnalyticsResponse leads = buildLeads(scopeId, window);
        DealAnalyticsResponse deals = buildDeals(scopeId, window);
        ActivityAnalyticsResponse activities = buildActivities(scopeId, window, now);
        AnalyticsTrendsResponse trends = buildTrends(scopeId, window);
        List<TeamMemberMetricsResponse> team =
                principal.getRole() == Role.ADMIN ? buildTeam(assignedTo, now) : List.of();

        return new AnalyticsSummaryResponse(window, leads, deals, activities, trends, team);
    }

    private UUID resolveScope(UUID assignedTo, UserPrincipal principal) {
        if (principal.getRole() == Role.SALES_REP) {
            if (assignedTo != null && !assignedTo.equals(principal.getId())) {
                throw new ForbiddenException("SALES_REP cannot request another user's analytics");
            }
            return principal.getId();
        }
        if (assignedTo == null) {
            return null;
        }
        if (!userRepository.existsById(assignedTo)) {
            throw new ResourceNotFoundException("User not found");
        }
        return assignedTo;
    }

    private LeadAnalyticsResponse buildLeads(UUID scopeId, AnalyticsRangeResponse window) {
        long total = analyticsRepository.countLeads(scopeId);
        long created = analyticsRepository.countLeadsCreated(scopeId, window.from(), window.toExclusive());
        Map<LeadStatus, Long> byStatus = new EnumMap<>(LeadStatus.class);
        for (LeadStatus status : LeadStatus.values()) {
            byStatus.put(status, 0L);
        }
        for (Object[] row : analyticsRepository.leadStatusCountsCreated(scopeId, window.from(), window.toExclusive())) {
            byStatus.put((LeadStatus) row[0], (Long) row[1]);
        }
        long converted = byStatus.get(LeadStatus.CONVERTED);
        long lost = byStatus.get(LeadStatus.LOST);
        BigDecimal conversionRate = conversionRate(converted, lost);
        List<LeadStatusCountResponse> statusRows = new ArrayList<>();
        for (LeadStatus status : LeadStatus.values()) {
            statusRows.add(new LeadStatusCountResponse(status, byStatus.get(status)));
        }
        return new LeadAnalyticsResponse(total, created, converted, lost, conversionRate, statusRows);
    }

    private DealAnalyticsResponse buildDeals(UUID scopeId, AnalyticsRangeResponse window) {
        long total = analyticsRepository.countDeals(scopeId);
        long created = analyticsRepository.countDealsCreated(scopeId, window.from(), window.toExclusive());
        long wonCount = analyticsRepository.countDealsByStage(scopeId, DealStage.CLOSED_WON);
        long lostCount = analyticsRepository.countDealsByStage(scopeId, DealStage.CLOSED_LOST);
        long openCount = analyticsRepository.countOpenDeals(scopeId);
        BigDecimal openValue = scaleMoney(analyticsRepository.sumOpenDealAmount(scopeId));
        BigDecimal weighted = scaleMoney(weighted(analyticsRepository.sumOpenAmountTimesProbability(scopeId)));
        BigDecimal wonValue = scaleMoney(analyticsRepository.sumDealAmountByStage(scopeId, DealStage.CLOSED_WON));
        BigDecimal lostValue = scaleMoney(analyticsRepository.sumDealAmountByStage(scopeId, DealStage.CLOSED_LOST));
        BigDecimal avgOpen = average(openValue, openCount);
        BigDecimal avgWon = average(wonValue, wonCount);

        Map<DealStage, DealStageMetricsResponse> byStage = new EnumMap<>(DealStage.class);
        for (DealStage stage : DealStage.values()) {
            byStage.put(stage, new DealStageMetricsResponse(stage, 0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
        }
        for (Object[] row : analyticsRepository.dealStageSnapshot(scopeId)) {
            DealStage stage = (DealStage) row[0];
            long count = (Long) row[1];
            BigDecimal amount = scaleMoney((BigDecimal) row[2]);
            byStage.put(stage, new DealStageMetricsResponse(stage, count, amount));
        }
        List<DealStageMetricsResponse> stageRows = new ArrayList<>();
        for (DealStage stage : DealStage.values()) {
            stageRows.add(byStage.get(stage));
        }
        return new DealAnalyticsResponse(
                total,
                created,
                openCount,
                wonCount,
                lostCount,
                openValue,
                weighted,
                wonValue,
                lostValue,
                avgOpen,
                avgWon,
                stageRows);
    }

    private ActivityAnalyticsResponse buildActivities(UUID scopeId, AnalyticsRangeResponse window, Instant now) {
        Instant from = window.from();
        Instant to = window.toExclusive();
        TaskActivityMetricsResponse tasks = new TaskActivityMetricsResponse(
                analyticsRepository.countTasksCreated(scopeId, from, to),
                analyticsRepository.countTasksCreatedWithStatus(scopeId, from, to, TaskStatus.OPEN),
                analyticsRepository.countTasksCreatedWithStatus(scopeId, from, to, TaskStatus.COMPLETED),
                analyticsRepository.countTasksCreatedWithStatus(scopeId, from, to, TaskStatus.CANCELLED),
                analyticsRepository.countOverdueOpenTasks(scopeId, now));
        MeetingActivityMetricsResponse meetings = new MeetingActivityMetricsResponse(
                analyticsRepository.countMeetingsCreated(scopeId, from, to),
                analyticsRepository.countMeetingsCreatedWithStatus(scopeId, from, to, MeetingStatus.SCHEDULED),
                analyticsRepository.countMeetingsCreatedWithStatus(scopeId, from, to, MeetingStatus.COMPLETED),
                analyticsRepository.countMeetingsCreatedWithStatus(scopeId, from, to, MeetingStatus.CANCELLED));
        CallActivityMetricsResponse calls = new CallActivityMetricsResponse(
                analyticsRepository.countCallsCreated(scopeId, from, to),
                analyticsRepository.countCallsCreatedWithStatus(scopeId, from, to, CallStatus.PLANNED),
                analyticsRepository.countCallsCreatedWithStatus(scopeId, from, to, CallStatus.COMPLETED),
                analyticsRepository.countCallsCreatedWithStatus(scopeId, from, to, CallStatus.CANCELLED));
        return new ActivityAnalyticsResponse(tasks, meetings, calls);
    }

    private AnalyticsTrendsResponse buildTrends(UUID scopeId, AnalyticsRangeResponse window) {
        List<CountPointResponse> leads = bucket(
                analyticsRepository.leadCreatedAt(scopeId, window.from(), window.toExclusive()), window);
        List<CountPointResponse> conversions = bucket(
                analyticsRepository.leadConvertedAt(scopeId, window.from(), window.toExclusive()), window);
        List<CountPointResponse> deals = bucket(
                analyticsRepository.dealCreatedAt(scopeId, window.from(), window.toExclusive()), window);
        Map<String, long[]> activity = emptyActivityBuckets(window);
        addActivity(activity, analyticsRepository.taskCreatedAt(scopeId, window.from(), window.toExclusive()), window, 0);
        addActivity(activity, analyticsRepository.meetingCreatedAt(scopeId, window.from(), window.toExclusive()), window, 1);
        addActivity(activity, analyticsRepository.callCreatedAt(scopeId, window.from(), window.toExclusive()), window, 2);
        List<ActivityTrendPointResponse> activityRows = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : activity.entrySet()) {
            long[] v = entry.getValue();
            activityRows.add(new ActivityTrendPointResponse(entry.getKey(), v[0], v[1], v[2]));
        }
        return new AnalyticsTrendsResponse(leads, conversions, deals, activityRows);
    }

    private List<TeamMemberMetricsResponse> buildTeam(UUID assignedTo, Instant now) {
        List<User> users = userRepository.findAll().stream()
                .filter(User::isActive)
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (assignedTo != null) {
            users = users.stream().filter(u -> u.getId().equals(assignedTo)).toList();
        }
        Map<UUID, long[]> openDeals = toCountSum(analyticsRepository.openDealsByOwner());
        Map<UUID, long[]> wonDeals = toCountSum(analyticsRepository.wonDealsByOwner());
        Map<UUID, Long> openTasks = toCount(analyticsRepository.openTasksByAssignee());
        Map<UUID, Long> overdue = toCount(analyticsRepository.overdueTasksByAssignee(now));
        Map<UUID, Long> meetings = toCount(analyticsRepository.scheduledMeetingsByAssignee());
        Map<UUID, Long> calls = toCount(analyticsRepository.plannedCallsByAssignee());

        List<TeamMemberMetricsResponse> rows = new ArrayList<>();
        for (User user : users) {
            UUID id = user.getId();
            long[] open = openDeals.getOrDefault(id, new long[] {0, 0});
            long[] won = wonDeals.getOrDefault(id, new long[] {0, 0});
            rows.add(new TeamMemberMetricsResponse(
                    id,
                    user.getFullName(),
                    open[0],
                    scaleMoney(BigDecimal.valueOf(open[1], 2)),
                    won[0],
                    scaleMoney(BigDecimal.valueOf(won[1], 2)),
                    openTasks.getOrDefault(id, 0L),
                    overdue.getOrDefault(id, 0L),
                    meetings.getOrDefault(id, 0L),
                    calls.getOrDefault(id, 0L)));
        }
        return rows;
    }

    /**
     * Owner aggregations store amount as unscaled cents-like long via movePointRight(2) to avoid float.
     */
    private static Map<UUID, long[]> toCountSum(List<Object[]> rows) {
        Map<UUID, long[]> map = new HashMap<>();
        for (Object[] row : rows) {
            UUID id = (UUID) row[0];
            long count = (Long) row[1];
            BigDecimal amount = row[2] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
            map.put(id, new long[] {count, amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue()});
        }
        return map;
    }

    private static Map<UUID, Long> toCount(List<Object[]> rows) {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], (Long) row[1]);
        }
        return map;
    }

    private List<CountPointResponse> bucket(List<Instant> timestamps, AnalyticsRangeResponse window) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String period : periods(window)) {
            counts.put(period, 0L);
        }
        for (Instant ts : timestamps) {
            String period = periodKey(ts, window.bucket());
            counts.computeIfPresent(period, (k, v) -> v + 1);
        }
        if (window.bucket() == TrendBucket.MONTH && window.preset() == com.flowcrm.analytics.dto.AnalyticsPreset.ALL_TIME) {
            trimLeadingEmptyMonths(counts);
        }
        List<CountPointResponse> points = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            points.add(new CountPointResponse(entry.getKey(), entry.getValue()));
        }
        return points;
    }

    private Map<String, long[]> emptyActivityBuckets(AnalyticsRangeResponse window) {
        Map<String, long[]> map = new LinkedHashMap<>();
        for (String period : periods(window)) {
            map.put(period, new long[] {0, 0, 0});
        }
        return map;
    }

    private void addActivity(
            Map<String, long[]> buckets, List<Instant> timestamps, AnalyticsRangeResponse window, int index) {
        for (Instant ts : timestamps) {
            String period = periodKey(ts, window.bucket());
            long[] row = buckets.get(period);
            if (row != null) {
                row[index]++;
            }
        }
    }

    private static List<String> periods(AnalyticsRangeResponse window) {
        List<String> periods = new ArrayList<>();
        if (window.bucket() == TrendBucket.MONTH) {
            YearMonth start = YearMonth.from(window.from().atZone(ZoneOffset.UTC));
            YearMonth lastInclusive = window.toExclusive().minusNanos(1).atZone(ZoneOffset.UTC).query(YearMonth::from);
            if (lastInclusive.isBefore(start)) {
                return periods;
            }
            for (YearMonth cursor = start; !cursor.isAfter(lastInclusive); cursor = cursor.plusMonths(1)) {
                periods.add(cursor.format(MONTH));
            }
            return periods;
        }
        LocalDate start = window.from().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endExclusive = window.toExclusive().atZone(ZoneOffset.UTC).toLocalDate();
        for (LocalDate cursor = start; cursor.isBefore(endExclusive); cursor = cursor.plusDays(1)) {
            periods.add(cursor.format(DAY));
        }
        return periods;
    }

    private static void trimLeadingEmptyMonths(Map<String, Long> counts) {
        if (counts.size() <= 24) {
            return;
        }
        String firstNonZero = null;
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (entry.getValue() > 0) {
                firstNonZero = entry.getKey();
                break;
            }
        }
        if (firstNonZero == null) {
            counts.clear();
            return;
        }
        List<String> keys = new ArrayList<>(counts.keySet());
        for (String key : keys) {
            if (key.equals(firstNonZero)) {
                break;
            }
            counts.remove(key);
        }
    }

    private static String periodKey(Instant instant, TrendBucket bucket) {
        if (bucket == TrendBucket.MONTH) {
            return YearMonth.from(instant.atZone(ZoneOffset.UTC)).format(MONTH);
        }
        return instant.atZone(ZoneOffset.UTC).toLocalDate().format(DAY);
    }

    static BigDecimal conversionRate(long converted, long lost) {
        long denom = converted + lost;
        if (denom == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(converted).divide(BigDecimal.valueOf(denom), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal weighted(BigDecimal amountTimesProbability) {
        return (amountTimesProbability == null ? BigDecimal.ZERO : amountTimesProbability)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(BigDecimal total, long count) {
        if (count == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleMoney(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return safe.setScale(2, RoundingMode.HALF_UP);
    }
}
