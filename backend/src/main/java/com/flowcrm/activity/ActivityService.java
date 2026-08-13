package com.flowcrm.activity;

import com.flowcrm.account.Account;
import com.flowcrm.account.AccountService;
import com.flowcrm.activity.dto.ActivityItemResponse;
import com.flowcrm.activity.dto.ActivityTimelineResponse;
import com.flowcrm.common.exception.BadRequestException;
import com.flowcrm.contact.Contact;
import com.flowcrm.contact.ContactService;
import com.flowcrm.deal.Deal;
import com.flowcrm.deal.DealService;
import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.enums.TaskStatus;
import com.flowcrm.lead.Lead;
import com.flowcrm.lead.LeadService;
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

    public ActivityService(
            LeadService leadService,
            AccountService accountService,
            ContactService contactService,
            DealService dealService,
            TaskRepository taskRepository) {
        this.leadService = leadService;
        this.accountService = accountService;
        this.contactService = contactService;
        this.dealService = dealService;
        this.taskRepository = taskRepository;
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
