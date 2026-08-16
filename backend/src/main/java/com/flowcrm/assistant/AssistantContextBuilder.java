package com.flowcrm.assistant;

import com.flowcrm.account.dto.AccountResponse;
import com.flowcrm.account.AccountService;
import com.flowcrm.activity.ActivityService;
import com.flowcrm.activity.dto.ActivityItemResponse;
import com.flowcrm.analytics.AnalyticsService;
import com.flowcrm.analytics.dto.AnalyticsSummaryResponse;
import com.flowcrm.analytics.dto.DealStageMetricsResponse;
import com.flowcrm.analytics.dto.LeadStatusCountResponse;
import com.flowcrm.analytics.dto.TeamMemberMetricsResponse;
import com.flowcrm.assistant.dto.AssistantContextRequest;
import com.flowcrm.assistant.dto.BuiltAssistantContext;
import com.flowcrm.call.Call;
import com.flowcrm.call.CallRepository;
import com.flowcrm.contact.Contact;
import com.flowcrm.contact.ContactRepository;
import com.flowcrm.contact.ContactService;
import com.flowcrm.contact.dto.ContactResponse;
import com.flowcrm.dashboard.DashboardService;
import com.flowcrm.dashboard.dto.DashboardSummaryResponse;
import com.flowcrm.deal.Deal;
import com.flowcrm.deal.DealRepository;
import com.flowcrm.deal.DealService;
import com.flowcrm.deal.dto.DealResponse;
import com.flowcrm.enums.DealStage;
import com.flowcrm.enums.LeadStatus;
import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.enums.Role;
import com.flowcrm.lead.LeadService;
import com.flowcrm.lead.dto.LeadResponse;
import com.flowcrm.meeting.Meeting;
import com.flowcrm.meeting.MeetingRepository;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.task.Task;
import com.flowcrm.task.TaskRepository;
import com.flowcrm.workqueue.WorkqueueService;
import com.flowcrm.workqueue.dto.WorkqueueItemResponse;
import com.flowcrm.workqueue.dto.WorkqueueResponse;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AssistantContextBuilder {

    static final int ITEM_LIMIT = 8;
    static final int MAX_CRM_CHARS = 12_000;

    private static final Set<DealStage> TERMINAL = EnumSet.of(DealStage.CLOSED_WON, DealStage.CLOSED_LOST);

    private final DashboardService dashboardService;
    private final AnalyticsService analyticsService;
    private final WorkqueueService workqueueService;
    private final LeadService leadService;
    private final AccountService accountService;
    private final ContactService contactService;
    private final DealService dealService;
    private final ActivityService activityService;
    private final TaskRepository taskRepository;
    private final MeetingRepository meetingRepository;
    private final CallRepository callRepository;
    private final DealRepository dealRepository;
    private final ContactRepository contactRepository;

    public AssistantContextBuilder(
            DashboardService dashboardService,
            AnalyticsService analyticsService,
            WorkqueueService workqueueService,
            LeadService leadService,
            AccountService accountService,
            ContactService contactService,
            DealService dealService,
            ActivityService activityService,
            TaskRepository taskRepository,
            MeetingRepository meetingRepository,
            CallRepository callRepository,
            DealRepository dealRepository,
            ContactRepository contactRepository) {
        this.dashboardService = dashboardService;
        this.analyticsService = analyticsService;
        this.workqueueService = workqueueService;
        this.leadService = leadService;
        this.accountService = accountService;
        this.contactService = contactService;
        this.dealService = dealService;
        this.activityService = activityService;
        this.taskRepository = taskRepository;
        this.meetingRepository = meetingRepository;
        this.callRepository = callRepository;
        this.dealRepository = dealRepository;
        this.contactRepository = contactRepository;
    }

    public BuiltAssistantContext build(UserPrincipal principal, AssistantContextRequest context) {
        StringBuilder data = new StringBuilder();
        data.append("Notes: Activity timeline is not a complete audit log. Pipeline numbers are current snapshots.\n");
        data.append("CURRENT USER: ")
                .append(principal.getFullName())
                .append(" | role=")
                .append(principal.getRole())
                .append('\n');

        appendGlobal(data, principal);

        RelatedRecordType entityType = null;
        UUID entityId = null;
        String label = null;
        if (context != null) {
            entityType = context.entityType();
            entityId = context.entityId();
            label = appendEntity(data, principal, entityType, entityId);
        }

        String crm = data.length() > MAX_CRM_CHARS ? data.substring(0, MAX_CRM_CHARS) + "\n[truncated]" : data.toString();
        return new BuiltAssistantContext(crm, entityType, entityId, label);
    }

    private void appendGlobal(StringBuilder data, UserPrincipal principal) {
        DashboardSummaryResponse dash = dashboardService.getSummary(principal);
        data.append("PIPELINE SNAPSHOT:\n");
        data.append("- totalLeads=").append(dash.totalLeads()).append('\n');
        for (LeadStatus status : LeadStatus.values()) {
            data.append("- leads.")
                    .append(status)
                    .append('=')
                    .append(dash.leadsByStatus().getOrDefault(status, 0L))
                    .append('\n');
        }
        data.append("- openDeals=")
                .append(dash.openDeals())
                .append(" openPipelineValue=")
                .append(money(dash.openPipelineValue()))
                .append(" weightedPipelineValue=")
                .append(money(dash.weightedPipelineValue()))
                .append('\n');
        data.append("- wonDeals=")
                .append(dash.wonDeals())
                .append(" wonValue=")
                .append(money(dash.wonDealValue()))
                .append('\n');
        data.append("- openTasks=")
                .append(dash.openTasks())
                .append(" overdueTasks=")
                .append(dash.overdueTasks())
                .append('\n');

        AnalyticsSummaryResponse analytics = analyticsService.summary("30d", null, null, null, principal);
        data.append("LAST_30_DAYS CREATED: leads=")
                .append(analytics.leads().created())
                .append(" deals=")
                .append(analytics.deals().created())
                .append(" conversionRate=")
                .append(analytics.leads().conversionRate())
                .append('\n');
        for (LeadStatusCountResponse row : analytics.leads().byStatus()) {
            data.append("- cohortLeads.").append(row.status()).append('=').append(row.count()).append('\n');
        }
        for (DealStageMetricsResponse row : analytics.deals().byStage()) {
            data.append("- deals.")
                    .append(row.stage())
                    .append(" count=")
                    .append(row.count())
                    .append(" amount=")
                    .append(money(row.totalAmount()))
                    .append('\n');
        }

        if (principal.getRole() == Role.ADMIN) {
            data.append("TEAM WORKLOAD (ADMIN):\n");
            int n = 0;
            for (TeamMemberMetricsResponse member : analytics.team()) {
                if (n++ >= ITEM_LIMIT) {
                    break;
                }
                data.append("- ")
                        .append(clip(member.displayName(), 80))
                        .append(" openDeals=")
                        .append(member.openDeals())
                        .append(" openPipeline=")
                        .append(money(member.openPipelineValue()))
                        .append(" overdueTasks=")
                        .append(member.overdueTasks())
                        .append('\n');
            }
        }

        WorkqueueResponse queue = workqueueService.get(null, principal);
        data.append("WORKQUEUE:\n");
        appendQueue(data, "overdueTasks", queue.overdueTasks());
        appendQueue(data, "dueTodayTasks", queue.dueTodayTasks());
        appendQueue(data, "upcomingTasks", queue.upcomingTasks());
        appendQueue(data, "todayMeetings", queue.todayMeetings());
        appendQueue(data, "upcomingMeetings", queue.upcomingMeetings());
        appendQueue(data, "todayCalls", queue.todayCalls());
        appendQueue(data, "upcomingCalls", queue.upcomingCalls());

        data.append("HIGH-VALUE OPEN DEALS:\n");
        List<Deal> openDeals = principal.getRole() == Role.ADMIN
                ? dealRepository.findTop8ByStageNotInOrderByAmountDesc(TERMINAL)
                : dealRepository.findTop8ByOwner_IdAndStageNotInOrderByAmountDesc(principal.getId(), TERMINAL);
        if (openDeals.isEmpty()) {
            data.append("- none\n");
        }
        for (Deal deal : openDeals) {
            data.append("- ")
                    .append(clip(deal.getName(), 80))
                    .append(" stage=")
                    .append(deal.getStage())
                    .append(" amount=")
                    .append(money(deal.getAmount()))
                    .append(" probability=")
                    .append(deal.getProbability())
                    .append("% account=")
                    .append(clip(deal.getAccount() == null ? "" : deal.getAccount().getName(), 60))
                    .append('\n');
        }
    }

    private String appendEntity(
            StringBuilder data, UserPrincipal principal, RelatedRecordType type, UUID id) {
        return switch (type) {
            case LEAD -> appendLead(data, principal, id);
            case ACCOUNT -> appendAccount(data, principal, id);
            case CONTACT -> appendContact(data, principal, id);
            case DEAL -> appendDeal(data, principal, id);
        };
    }

    private String appendLead(StringBuilder data, UserPrincipal principal, UUID id) {
        LeadResponse lead = leadService.getById(id, principal);
        data.append("SELECTED LEAD:\n");
        data.append("- name=").append(clip(lead.fullName(), 120)).append('\n');
        data.append("- company=").append(clip(lead.company(), 80)).append('\n');
        data.append("- status=").append(lead.status()).append(" source=").append(lead.source()).append('\n');
        data.append("- email=").append(clip(lead.email(), 80)).append(" phone=").append(clip(lead.phone(), 40)).append('\n');
        data.append("- createdAt=").append(lead.createdAt()).append('\n');
        data.append("- convertedAt=").append(lead.convertedAt()).append(" convertedDeal=").append(clip(lead.convertedDealName(), 80)).append('\n');
        appendRelatedWork(data, RelatedRecordType.LEAD, id);
        appendActivity(data, RelatedRecordType.LEAD, id, principal);
        return lead.fullName();
    }

    private String appendAccount(StringBuilder data, UserPrincipal principal, UUID id) {
        AccountResponse account = accountService.getById(id, principal);
        data.append("SELECTED ACCOUNT:\n");
        data.append("- name=").append(clip(account.name(), 120)).append('\n');
        data.append("- industry=").append(clip(account.industry(), 80)).append('\n');
        data.append("- phone=").append(clip(account.phone(), 40)).append('\n');
        data.append("- description=").append(clip(account.description(), 200)).append('\n');
        data.append("RELATED CONTACTS:\n");
        List<Contact> contacts = contactRepository.findTop8ByAccount_IdOrderByLastNameAsc(id);
        if (contacts.isEmpty()) {
            data.append("- none\n");
        }
        for (Contact contact : contacts) {
            data.append("- ")
                    .append(clip(contact.getFirstName() + " " + contact.getLastName(), 80))
                    .append(" title=")
                    .append(clip(contact.getJobTitle(), 60))
                    .append('\n');
        }
        data.append("RELATED DEALS:\n");
        for (Deal deal : dealRepository.findTop8ByAccount_IdOrderByAmountDesc(id)) {
            data.append("- ")
                    .append(clip(deal.getName(), 80))
                    .append(" stage=")
                    .append(deal.getStage())
                    .append(" amount=")
                    .append(money(deal.getAmount()))
                    .append('\n');
        }
        appendRelatedWork(data, RelatedRecordType.ACCOUNT, id);
        appendActivity(data, RelatedRecordType.ACCOUNT, id, principal);
        return account.name();
    }

    private String appendContact(StringBuilder data, UserPrincipal principal, UUID id) {
        ContactResponse contact = contactService.getById(id, principal);
        data.append("SELECTED CONTACT:\n");
        data.append("- name=").append(clip(contact.firstName() + " " + contact.lastName(), 120)).append('\n');
        data.append("- account=").append(clip(contact.accountName(), 80)).append('\n');
        data.append("- title=").append(clip(contact.jobTitle(), 80)).append('\n');
        data.append("- email=").append(clip(contact.email(), 80)).append(" phone=").append(clip(contact.phone(), 40)).append('\n');
        data.append("RELATED DEALS:\n");
        List<Deal> deals = dealRepository.findTop8ByPrimaryContact_IdOrderByUpdatedAtDesc(id);
        if (deals.isEmpty()) {
            data.append("- none\n");
        }
        for (Deal deal : deals) {
            data.append("- ")
                    .append(clip(deal.getName(), 80))
                    .append(" stage=")
                    .append(deal.getStage())
                    .append(" amount=")
                    .append(money(deal.getAmount()))
                    .append('\n');
        }
        appendRelatedWork(data, RelatedRecordType.CONTACT, id);
        appendActivity(data, RelatedRecordType.CONTACT, id, principal);
        return contact.firstName() + " " + contact.lastName();
    }

    private String appendDeal(StringBuilder data, UserPrincipal principal, UUID id) {
        DealResponse deal = dealService.getById(id, principal);
        data.append("SELECTED DEAL:\n");
        data.append("- name=").append(clip(deal.name(), 120)).append('\n');
        data.append("- stage=").append(deal.stage()).append(" probability=").append(deal.probability()).append("%\n");
        data.append("- amount=").append(money(deal.amount())).append(" currency=").append(deal.currency()).append('\n');
        data.append("- account=").append(clip(deal.accountName(), 80)).append('\n');
        data.append("- contact=").append(clip(deal.primaryContactName(), 80)).append('\n');
        data.append("- expectedClose=").append(deal.expectedCloseDate()).append('\n');
        data.append("- description=").append(clip(deal.description(), 200)).append('\n');
        appendRelatedWork(data, RelatedRecordType.DEAL, id);
        appendActivity(data, RelatedRecordType.DEAL, id, principal);
        return deal.name();
    }

    private void appendRelatedWork(StringBuilder data, RelatedRecordType type, UUID id) {
        List<Task> tasks =
                switch (type) {
                    case LEAD -> taskRepository.findByLead_IdOrderByCreatedAtDesc(id);
                    case ACCOUNT -> taskRepository.findByAccount_IdOrderByCreatedAtDesc(id);
                    case CONTACT -> taskRepository.findByContact_IdOrderByCreatedAtDesc(id);
                    case DEAL -> taskRepository.findByDeal_IdOrderByCreatedAtDesc(id);
                };
        data.append("RELATED TASKS:\n");
        writeLimited(data, tasks, ITEM_LIMIT, task -> "- " + clip(task.getTitle(), 80) + " status=" + task.getStatus()
                + " dueAt=" + task.getDueAt());

        List<Meeting> meetings =
                switch (type) {
                    case LEAD -> meetingRepository.findByLead_IdOrderByCreatedAtDesc(id);
                    case ACCOUNT -> meetingRepository.findByAccount_IdOrderByCreatedAtDesc(id);
                    case CONTACT -> meetingRepository.findByContact_IdOrderByCreatedAtDesc(id);
                    case DEAL -> meetingRepository.findByDeal_IdOrderByCreatedAtDesc(id);
                };
        data.append("RELATED MEETINGS:\n");
        writeLimited(data, meetings, ITEM_LIMIT, meeting -> "- " + clip(meeting.getTitle(), 80) + " status="
                + meeting.getStatus() + " startAt=" + meeting.getStartAt());

        List<Call> calls =
                switch (type) {
                    case LEAD -> callRepository.findByLead_IdOrderByCreatedAtDesc(id);
                    case ACCOUNT -> callRepository.findByAccount_IdOrderByCreatedAtDesc(id);
                    case CONTACT -> callRepository.findByContact_IdOrderByCreatedAtDesc(id);
                    case DEAL -> callRepository.findByDeal_IdOrderByCreatedAtDesc(id);
                };
        data.append("RELATED CALLS:\n");
        writeLimited(data, calls, ITEM_LIMIT, call -> "- " + clip(call.getTitle(), 80) + " status=" + call.getStatus()
                + " scheduledAt=" + call.getScheduledAt());
    }

    private void appendActivity(StringBuilder data, RelatedRecordType type, UUID id, UserPrincipal principal) {
        data.append("RECENT ACTIVITY (not a full audit log):\n");
        List<ActivityItemResponse> items = activityService.timeline(type, id, principal).items();
        writeLimited(data, items, ITEM_LIMIT, item -> "- " + item.timestamp() + " " + item.type() + " " + clip(item.title(), 100));
    }

    private static void appendQueue(StringBuilder data, String label, List<WorkqueueItemResponse> items) {
        data.append(label).append(":\n");
        writeLimited(
                data,
                items,
                ITEM_LIMIT,
                item -> "- " + clip(item.title(), 80) + " " + item.itemType() + " " + item.urgency() + " at="
                        + item.timestamp() + " related=" + item.relatedType() + "/" + clip(item.relatedName(), 60));
    }

    private static <T> void writeLimited(
            StringBuilder data, List<T> items, int limit, java.util.function.Function<T, String> line) {
        if (items == null || items.isEmpty()) {
            data.append("- none\n");
            return;
        }
        int n = 0;
        for (T item : items) {
            if (n++ >= limit) {
                break;
            }
            data.append(line.apply(item)).append('\n');
        }
    }

    private static String money(BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.replace('\n', ' ').trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
