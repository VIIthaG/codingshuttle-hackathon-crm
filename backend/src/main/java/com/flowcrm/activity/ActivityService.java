package com.flowcrm.activity;

import com.flowcrm.account.Account;
import com.flowcrm.account.AccountService;
import com.flowcrm.activity.dto.ActivityItemResponse;
import com.flowcrm.activity.dto.ActivityTimelineResponse;
import com.flowcrm.common.exception.BadRequestException;
import com.flowcrm.contact.Contact;
import com.flowcrm.contact.ContactService;
import com.flowcrm.call.Call;
import com.flowcrm.call.CallRepository;
import com.flowcrm.deal.Deal;
import com.flowcrm.deal.DealService;
import com.flowcrm.enums.CallStatus;
import com.flowcrm.enums.MeetingStatus;
import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.enums.TaskStatus;
import com.flowcrm.lead.Lead;
import com.flowcrm.lead.LeadService;
import com.flowcrm.meeting.Meeting;
import com.flowcrm.meeting.MeetingRepository;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.task.Task;
import com.flowcrm.task.TaskRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {

    private static final Duration MEANINGFUL_UPDATE = Duration.ofSeconds(1);

    private final LeadService leadService;
    private final AccountService accountService;
    private final ContactService contactService;
    private final DealService dealService;
    private final TaskRepository taskRepository;
    private final MeetingRepository meetingRepository;
    private final CallRepository callRepository;

    public ActivityService(
            LeadService leadService,
            AccountService accountService,
            ContactService contactService,
            DealService dealService,
            TaskRepository taskRepository,
            MeetingRepository meetingRepository,
            CallRepository callRepository) {
        this.leadService = leadService;
        this.accountService = accountService;
        this.contactService = contactService;
        this.dealService = dealService;
        this.taskRepository = taskRepository;
        this.meetingRepository = meetingRepository;
        this.callRepository = callRepository;
    }

    @Transactional(readOnly = true)
    public ActivityTimelineResponse timeline(RelatedRecordType entityType, UUID entityId, UserPrincipal principal) {
        if (entityType == null || entityId == null) {
            throw new BadRequestException("entityType and entityId are required");
        }

        return switch (entityType) {
            case LEAD -> leadTimeline(entityId, principal);
            case ACCOUNT -> accountTimeline(entityId, principal);
            case CONTACT -> contactTimeline(entityId, principal);
            case DEAL -> dealTimeline(entityId, principal);
        };
    }

    private ActivityTimelineResponse leadTimeline(UUID id, UserPrincipal principal) {
        Lead lead = leadService.requireAccessibleLead(id, principal);
        List<ActivityItemResponse> items = new ArrayList<>();
        String actor = lead.getAssignedTo().getFullName();
        items.add(item(
                "lead:" + id + ":created",
                "RECORD_CREATED",
                "Lead created",
                lead.getFullName(),
                lead.getCreatedAt(),
                actor,
                null,
                RelatedRecordType.LEAD,
                id,
                lead.getFullName(),
                null));
        if (isMeaningfulUpdate(lead.getCreatedAt(), lead.getUpdatedAt())) {
            items.add(item(
                    "lead:" + id + ":updated",
                    "RECORD_UPDATED",
                    "Lead updated",
                    null,
                    lead.getUpdatedAt(),
                    actor,
                    lead.getStatus().name(),
                    RelatedRecordType.LEAD,
                    id,
                    lead.getFullName(),
                    null));
        }
        if (lead.getConvertedAt() != null) {
            String accountName = lead.getConvertedAccount() == null ? null : lead.getConvertedAccount().getName();
            items.add(item(
                    "lead:" + id + ":converted",
                    "LEAD_CONVERTED",
                    "Lead converted",
                    accountName == null ? null : "Converted to " + accountName,
                    lead.getConvertedAt(),
                    actor,
                    "CONVERTED",
                    RelatedRecordType.ACCOUNT,
                    lead.getConvertedAccount() == null ? null : lead.getConvertedAccount().getId(),
                    accountName,
                    null));
        }
        addTaskItems(items, taskRepository.findByLead_IdOrderByCreatedAtDesc(id));
        addMeetingItems(items, meetingRepository.findByLead_IdOrderByCreatedAtDesc(id));
        addCallItems(items, callRepository.findByLead_IdOrderByCreatedAtDesc(id));
        return new ActivityTimelineResponse(RelatedRecordType.LEAD, id, lead.getFullName(), newestFirst(items));
    }

    private ActivityTimelineResponse accountTimeline(UUID id, UserPrincipal principal) {
        Account account = accountService.requireAccessibleAccount(id, principal);
        List<ActivityItemResponse> items = new ArrayList<>();
        String actor = account.getOwner().getFullName();
        items.add(item(
                "account:" + id + ":created",
                "RECORD_CREATED",
                "Account created",
                account.getName(),
                account.getCreatedAt(),
                actor,
                null,
                RelatedRecordType.ACCOUNT,
                id,
                account.getName(),
                null));
        if (isMeaningfulUpdate(account.getCreatedAt(), account.getUpdatedAt())) {
            items.add(item(
                    "account:" + id + ":updated",
                    "RECORD_UPDATED",
                    "Account updated",
                    null,
                    account.getUpdatedAt(),
                    actor,
                    null,
                    RelatedRecordType.ACCOUNT,
                    id,
                    account.getName(),
                    null));
        }
        addTaskItems(items, taskRepository.findByAccount_IdOrderByCreatedAtDesc(id));
        addMeetingItems(items, meetingRepository.findByAccount_IdOrderByCreatedAtDesc(id));
        addCallItems(items, callRepository.findByAccount_IdOrderByCreatedAtDesc(id));
        return new ActivityTimelineResponse(RelatedRecordType.ACCOUNT, id, account.getName(), newestFirst(items));
    }

    private ActivityTimelineResponse contactTimeline(UUID id, UserPrincipal principal) {
        Contact contact = contactService.requireAccessibleContact(id, principal);
        String name = (contact.getFirstName() + " " + contact.getLastName()).trim();
        List<ActivityItemResponse> items = new ArrayList<>();
        String actor = contact.getOwner().getFullName();
        items.add(item(
                "contact:" + id + ":created",
                "RECORD_CREATED",
                "Contact created",
                name,
                contact.getCreatedAt(),
                actor,
                null,
                RelatedRecordType.CONTACT,
                id,
                name,
                null));
        if (isMeaningfulUpdate(contact.getCreatedAt(), contact.getUpdatedAt())) {
            items.add(item(
                    "contact:" + id + ":updated",
                    "RECORD_UPDATED",
                    "Contact updated",
                    null,
                    contact.getUpdatedAt(),
                    actor,
                    null,
                    RelatedRecordType.CONTACT,
                    id,
                    name,
                    null));
        }
        addTaskItems(items, taskRepository.findByContact_IdOrderByCreatedAtDesc(id));
        addMeetingItems(items, meetingRepository.findByContact_IdOrderByCreatedAtDesc(id));
        addCallItems(items, callRepository.findByContact_IdOrderByCreatedAtDesc(id));
        return new ActivityTimelineResponse(RelatedRecordType.CONTACT, id, name, newestFirst(items));
    }

    private ActivityTimelineResponse dealTimeline(UUID id, UserPrincipal principal) {
        Deal deal = dealService.requireAccessibleDeal(id, principal);
        List<ActivityItemResponse> items = new ArrayList<>();
        String actor = deal.getOwner().getFullName();
        items.add(item(
                "deal:" + id + ":created",
                "RECORD_CREATED",
                "Deal created",
                deal.getName(),
                deal.getCreatedAt(),
                actor,
                deal.getStage().name(),
                RelatedRecordType.DEAL,
                id,
                deal.getName(),
                null));
        if (isMeaningfulUpdate(deal.getCreatedAt(), deal.getUpdatedAt())) {
            items.add(item(
                    "deal:" + id + ":updated",
                    "RECORD_UPDATED",
                    "Deal updated",
                    null,
                    deal.getUpdatedAt(),
                    actor,
                    deal.getStage().name(),
                    RelatedRecordType.DEAL,
                    id,
                    deal.getName(),
                    null));
        }
        addTaskItems(items, taskRepository.findByDeal_IdOrderByCreatedAtDesc(id));
        addMeetingItems(items, meetingRepository.findByDeal_IdOrderByCreatedAtDesc(id));
        addCallItems(items, callRepository.findByDeal_IdOrderByCreatedAtDesc(id));
        return new ActivityTimelineResponse(RelatedRecordType.DEAL, id, deal.getName(), newestFirst(items));
    }

    private void addTaskItems(List<ActivityItemResponse> items, List<Task> tasks) {
        for (Task task : tasks) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("dueAt", task.getDueAt().toString());
            if (task.getReminderAt() != null) {
                meta.put("reminderAt", task.getReminderAt().toString());
            }
            items.add(item(
                    "task:" + task.getId() + ":created",
                    "TASK_CREATED",
                    "Task created",
                    task.getTitle(),
                    task.getCreatedAt(),
                    task.getAssignedTo().getFullName(),
                    TaskStatus.OPEN.name(),
                    task.relatedType(),
                    task.relatedId(),
                    task.relatedName(),
                    meta));
            if (task.getStatus() == TaskStatus.COMPLETED) {
                items.add(item(
                        "task:" + task.getId() + ":completed",
                        "TASK_COMPLETED",
                        "Task completed",
                        task.getTitle(),
                        task.getUpdatedAt(),
                        task.getAssignedTo().getFullName(),
                        TaskStatus.COMPLETED.name(),
                        task.relatedType(),
                        task.relatedId(),
                        task.relatedName(),
                        meta));
            } else if (task.getStatus() == TaskStatus.CANCELLED) {
                items.add(item(
                        "task:" + task.getId() + ":cancelled",
                        "TASK_CANCELLED",
                        "Task cancelled",
                        task.getTitle(),
                        task.getUpdatedAt(),
                        task.getAssignedTo().getFullName(),
                        TaskStatus.CANCELLED.name(),
                        task.relatedType(),
                        task.relatedId(),
                        task.relatedName(),
                        meta));
            }
        }
    }

    private void addMeetingItems(List<ActivityItemResponse> items, List<Meeting> meetings) {
        for (Meeting meeting : meetings) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("startAt", meeting.getStartAt().toString());
            meta.put("endAt", meeting.getEndAt().toString());
            items.add(item(
                    "meeting:" + meeting.getId() + ":created",
                    "MEETING_CREATED",
                    "Meeting scheduled",
                    meeting.getTitle(),
                    meeting.getCreatedAt(),
                    meeting.getAssignedTo().getFullName(),
                    MeetingStatus.SCHEDULED.name(),
                    meeting.relatedType(),
                    meeting.relatedId(),
                    meeting.relatedName(),
                    meta));
            if (meeting.getStatus() == MeetingStatus.COMPLETED) {
                items.add(item(
                        "meeting:" + meeting.getId() + ":completed",
                        "MEETING_COMPLETED",
                        "Meeting completed",
                        meeting.getTitle(),
                        meeting.getUpdatedAt(),
                        meeting.getAssignedTo().getFullName(),
                        MeetingStatus.COMPLETED.name(),
                        meeting.relatedType(),
                        meeting.relatedId(),
                        meeting.relatedName(),
                        meta));
            } else if (meeting.getStatus() == MeetingStatus.CANCELLED) {
                items.add(item(
                        "meeting:" + meeting.getId() + ":cancelled",
                        "MEETING_CANCELLED",
                        "Meeting cancelled",
                        meeting.getTitle(),
                        meeting.getUpdatedAt(),
                        meeting.getAssignedTo().getFullName(),
                        MeetingStatus.CANCELLED.name(),
                        meeting.relatedType(),
                        meeting.relatedId(),
                        meeting.relatedName(),
                        meta));
            }
        }
    }

    private void addCallItems(List<ActivityItemResponse> items, List<Call> calls) {
        for (Call call : calls) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("direction", call.getDirection().name());
            meta.put("scheduledAt", call.getScheduledAt().toString());
            if (call.getOutcome() != null) {
                meta.put("outcome", call.getOutcome());
            }
            items.add(item(
                    "call:" + call.getId() + ":created",
                    "CALL_CREATED",
                    "Call planned",
                    call.getTitle(),
                    call.getCreatedAt(),
                    call.getAssignedTo().getFullName(),
                    CallStatus.PLANNED.name(),
                    call.relatedType(),
                    call.relatedId(),
                    call.relatedName(),
                    meta));
            if (call.getStatus() == CallStatus.COMPLETED) {
                items.add(item(
                        "call:" + call.getId() + ":completed",
                        "CALL_COMPLETED",
                        "Call completed",
                        call.getTitle(),
                        call.getUpdatedAt(),
                        call.getAssignedTo().getFullName(),
                        CallStatus.COMPLETED.name(),
                        call.relatedType(),
                        call.relatedId(),
                        call.relatedName(),
                        meta));
            } else if (call.getStatus() == CallStatus.CANCELLED) {
                items.add(item(
                        "call:" + call.getId() + ":cancelled",
                        "CALL_CANCELLED",
                        "Call cancelled",
                        call.getTitle(),
                        call.getUpdatedAt(),
                        call.getAssignedTo().getFullName(),
                        CallStatus.CANCELLED.name(),
                        call.relatedType(),
                        call.relatedId(),
                        call.relatedName(),
                        meta));
            }
        }
    }

    private boolean isMeaningfulUpdate(Instant createdAt, Instant updatedAt) {
        if (createdAt == null || updatedAt == null) {
            return false;
        }
        return Duration.between(createdAt, updatedAt).compareTo(MEANINGFUL_UPDATE) >= 0;
    }

    private List<ActivityItemResponse> newestFirst(List<ActivityItemResponse> items) {
        items.sort(Comparator.comparing(ActivityItemResponse::timestamp, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(ActivityItemResponse::id));
        return items;
    }

    private ActivityItemResponse item(
            String id,
            String type,
            String title,
            String description,
            Instant timestamp,
            String actorName,
            String status,
            RelatedRecordType relatedType,
            UUID relatedId,
            String relatedName,
            Map<String, Object> metadata) {
        return new ActivityItemResponse(
                id, type, title, description, timestamp, actorName, status, relatedType, relatedId, relatedName, metadata);
    }
}
