package com.flowcrm.lead;

import com.flowcrm.account.Account;
import com.flowcrm.account.AccountService;
import com.flowcrm.account.dto.AccountCreateRequest;
import com.flowcrm.account.dto.AccountResponse;
import com.flowcrm.common.exception.BadRequestException;
import com.flowcrm.common.exception.ConflictException;
import com.flowcrm.common.exception.ForbiddenException;
import com.flowcrm.common.exception.InvalidStatusTransitionException;
import com.flowcrm.common.exception.ResourceNotFoundException;
import com.flowcrm.contact.Contact;
import com.flowcrm.contact.ContactService;
import com.flowcrm.contact.dto.ContactCreateRequest;
import com.flowcrm.contact.dto.ContactResponse;
import com.flowcrm.dashboard.DashboardService;
import com.flowcrm.deal.Deal;
import com.flowcrm.deal.DealRepository;
import com.flowcrm.deal.DealService;
import com.flowcrm.deal.dto.DealCreateRequest;
import com.flowcrm.deal.dto.DealResponse;
import com.flowcrm.enums.DealStage;
import com.flowcrm.enums.LeadStatus;
import com.flowcrm.enums.Role;
import com.flowcrm.idempotency.IdempotencyOperations;
import com.flowcrm.idempotency.IdempotencyService;
import com.flowcrm.lead.dto.LeadConvertIdempotencyPayload;
import com.flowcrm.lead.dto.LeadConvertRequest;
import com.flowcrm.lead.dto.LeadCreateRequest;
import com.flowcrm.lead.dto.LeadResponse;
import com.flowcrm.lead.dto.LeadStatusUpdateRequest;
import com.flowcrm.lead.dto.LeadUpdateRequest;
import com.flowcrm.meeting.MeetingRepository;
import com.flowcrm.call.CallRepository;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.task.TaskRepository;
import com.flowcrm.user.User;
import com.flowcrm.user.UserRepository;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadService {

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final AccountService accountService;
    private final ContactService contactService;
    private final DealService dealService;
    private final DealRepository dealRepository;
    private final TaskRepository taskRepository;
    private final MeetingRepository meetingRepository;
    private final CallRepository callRepository;
    private final DashboardService dashboardService;
    private final IdempotencyService idempotencyService;
    private final LeadService self;

    public LeadService(
            LeadRepository leadRepository,
            UserRepository userRepository,
            AccountService accountService,
            ContactService contactService,
            DealService dealService,
            DealRepository dealRepository,
            TaskRepository taskRepository,
            MeetingRepository meetingRepository,
            CallRepository callRepository,
            DashboardService dashboardService,
            IdempotencyService idempotencyService,
            @Lazy LeadService self) {
        this.leadRepository = leadRepository;
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.contactService = contactService;
        this.dealService = dealService;
        this.dealRepository = dealRepository;
        this.taskRepository = taskRepository;
        this.meetingRepository = meetingRepository;
        this.callRepository = callRepository;
        this.dashboardService = dashboardService;
        this.idempotencyService = idempotencyService;
        this.self = self;
    }

    @Transactional
    public LeadResponse create(LeadCreateRequest request, UserPrincipal principal) {
        User assignee = resolveAssigneeForCreate(request.assignedToId(), principal);

        Lead lead = new Lead();
        lead.setFullName(request.fullName().trim());
        lead.setEmail(normalizeOptionalEmail(request.email()));
        lead.setPhone(trimToNull(request.phone()));
        lead.setCompany(trimToNull(request.company()));
        lead.setSource(request.source());
        lead.setStatus(request.status() != null ? request.status() : LeadStatus.NEW);
        lead.setAssignedTo(assignee);

        LeadResponse response = toResponse(leadRepository.save(lead));
        dashboardService.invalidateAllSummaries();
        return response;
    }

    /**
     * Idempotent create when {@code idempotencyKey} is present (validated by the controller).
     */
    public LeadResponse create(LeadCreateRequest request, UserPrincipal principal, String idempotencyKey) {
        return idempotencyService.execute(
                principal.getId(),
                IdempotencyOperations.LEADS_CREATE,
                idempotencyKey,
                request,
                LeadResponse.class,
                HttpStatus.CREATED.value(),
                () -> self.create(request, principal));
    }

    @Transactional(readOnly = true)
    public Page<LeadResponse> list(LeadStatus status, UserPrincipal principal, Pageable pageable) {
        Page<Lead> page;
        if (principal.getRole() == Role.ADMIN) {
            page = status == null
                    ? leadRepository.findAll(pageable)
                    : leadRepository.findByStatus(status, pageable);
        } else {
            User current = requireUser(principal.getId());
            page = status == null
                    ? leadRepository.findByAssignedTo(current, pageable)
                    : leadRepository.findByAssignedToAndStatus(current, status, pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public LeadResponse getById(UUID id, UserPrincipal principal) {
        Lead lead = requireLead(id);
        assertCanAccess(lead, principal);
        return toResponse(lead);
    }

    @Transactional
    public LeadResponse update(UUID id, LeadUpdateRequest request, UserPrincipal principal) {
        Lead lead = requireLead(id);
        assertCanAccess(lead, principal);

        User assignee = resolveAssigneeForUpdate(request.assignedToId(), principal, lead);

        lead.setFullName(request.fullName().trim());
        lead.setEmail(normalizeOptionalEmail(request.email()));
        lead.setPhone(trimToNull(request.phone()));
        lead.setCompany(trimToNull(request.company()));
        lead.setSource(request.source());
        applyStatusTransition(lead, request.status());
        lead.setAssignedTo(assignee);

        LeadResponse response = toResponse(leadRepository.save(lead));
        dashboardService.invalidateAllSummaries();
        return response;
    }

    @Transactional
    public LeadResponse changeStatus(UUID id, LeadStatusUpdateRequest request, UserPrincipal principal) {
        Lead lead = requireLead(id);
        assertCanAccess(lead, principal);
        applyStatusTransition(lead, request.status());
        LeadResponse response = toResponse(leadRepository.save(lead));
        dashboardService.invalidateAllSummaries();
        return response;
    }

    @Transactional
    public void delete(UUID id, UserPrincipal principal) {
        Lead lead = requireLead(id);
        assertCanAccess(lead, principal);
        if (taskRepository.existsByLead_Id(id)) {
            throw new ConflictException("Cannot delete lead while tasks still reference it");
        }
        if (meetingRepository.existsByLead_Id(id)) {
            throw new ConflictException("Cannot delete lead while meetings still reference it");
        }
        if (callRepository.existsByLead_Id(id)) {
            throw new ConflictException("Cannot delete lead while calls still reference it");
        }
        leadRepository.delete(lead);
        dashboardService.invalidateAllSummaries();
    }

    public LeadResponse convert(UUID leadId, LeadConvertRequest request, UserPrincipal principal, String idempotencyKey) {
        return idempotencyService.execute(
                principal.getId(),
                IdempotencyOperations.LEADS_CONVERT,
                idempotencyKey,
                new LeadConvertIdempotencyPayload(leadId, request),
                LeadResponse.class,
                HttpStatus.OK.value(),
                () -> self.convert(leadId, request, principal));
    }

    @Transactional
    public LeadResponse convert(UUID leadId, LeadConvertRequest request, UserPrincipal principal) {
        Lead lead = leadRepository.findByIdForUpdate(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found: " + leadId));
        assertCanAccess(lead, principal);

        if (lead.getStatus() != LeadStatus.QUALIFIED) {
            throw new ConflictException("Lead cannot be converted from status " + lead.getStatus());
        }
        if (lead.getConvertedAt() != null) {
            throw new ConflictException("Lead has already been converted");
        }

        UUID ownerId = lead.getAssignedTo().getId();
        Account account = resolveConversionAccount(request, principal, ownerId, lead);
        Contact contact = resolveConversionContact(request, principal, ownerId, account, lead);

        Deal deal = null;
        if (Boolean.TRUE.equals(request.createDeal())) {
            if (request.dealName() == null || request.dealName().isBlank()) {
                throw new BadRequestException("Deal name is required when createDeal is true");
            }
            DealResponse createdDeal = dealService.create(
                    new DealCreateRequest(
                            request.dealName().trim(),
                            account.getId(),
                            contact.getId(),
                            ownerId,
                            DealStage.PROSPECTING,
                            request.amount(),
                            request.currency(),
                            null,
                            request.expectedCloseDate(),
                            trimToNull(request.description()),
                            null),
                    principal);
            deal = dealRepository.findById(createdDeal.id())
                    .orElseThrow(() -> new ResourceNotFoundException("Deal not found: " + createdDeal.id()));
        }

        lead.setStatus(LeadStatus.CONVERTED);
        lead.setConvertedAt(java.time.Instant.now());
        lead.setConvertedAccount(account);
        lead.setConvertedContact(contact);
        lead.setConvertedDeal(deal);

        LeadResponse response = toResponse(leadRepository.save(lead));
        dashboardService.invalidateAllSummaries();
        return response;
    }

    /**
     * Loads a lead and enforces role-aware visibility. Used by other domains (e.g. tasks).
     */
    @Transactional(readOnly = true)
    public Lead requireAccessibleLead(UUID id, UserPrincipal principal) {
        Lead lead = requireLead(id);
        assertCanAccess(lead, principal);
        return lead;
    }

    private User resolveAssigneeForCreate(UUID assignedToId, UserPrincipal principal) {
        if (assignedToId == null) {
            return requireUser(principal.getId());
        }
        if (!assignedToId.equals(principal.getId()) && principal.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only admins can assign leads to other users");
        }
        return requireUser(assignedToId);
    }

    private User resolveAssigneeForUpdate(UUID assignedToId, UserPrincipal principal, Lead existing) {
        if (assignedToId.equals(existing.getAssignedTo().getId())) {
            return existing.getAssignedTo();
        }
        if (principal.getRole() != Role.ADMIN && !assignedToId.equals(principal.getId())) {
            throw new ForbiddenException("Only admins can reassign leads to other users");
        }
        return requireUser(assignedToId);
    }

    private void assertCanAccess(Lead lead, UserPrincipal principal) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (!lead.getAssignedTo().getId().equals(principal.getId())) {
            throw new ForbiddenException("You do not have access to this lead");
        }
    }

    private void applyStatusTransition(Lead lead, LeadStatus targetStatus) {
        LeadStatus current = lead.getStatus();
        if (current == targetStatus) {
            return;
        }
        if (targetStatus == LeadStatus.CONVERTED) {
            throw new InvalidStatusTransitionException(
                    "CONVERTED can only be reached through POST /api/v1/leads/{id}/convert");
        }
        if (!LeadStatusTransitions.canTransition(current, targetStatus)) {
            throw new InvalidStatusTransitionException(
                    "Cannot transition lead status from " + current + " to " + targetStatus);
        }
        lead.setStatus(targetStatus);
    }

    private Lead requireLead(UUID id) {
        return leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found: " + id));
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private LeadResponse toResponse(Lead lead) {
        User assignee = lead.getAssignedTo();
        Account convertedAccount = lead.getConvertedAccount();
        Contact convertedContact = lead.getConvertedContact();
        Deal convertedDeal = lead.getConvertedDeal();
        String convertedContactName = null;
        if (convertedContact != null) {
            convertedContactName = (convertedContact.getFirstName() + " " + convertedContact.getLastName()).trim();
        }
        return new LeadResponse(
                lead.getId(),
                lead.getFullName(),
                lead.getEmail(),
                lead.getPhone(),
                lead.getCompany(),
                lead.getSource(),
                lead.getStatus(),
                assignee.getId(),
                assignee.getFullName(),
                lead.getCreatedAt(),
                lead.getUpdatedAt(),
                lead.getConvertedAt(),
                convertedAccount == null ? null : convertedAccount.getId(),
                convertedAccount == null ? null : convertedAccount.getName(),
                convertedContact == null ? null : convertedContact.getId(),
                convertedContactName,
                convertedDeal == null ? null : convertedDeal.getId(),
                convertedDeal == null ? null : convertedDeal.getName());
    }

    private Account resolveConversionAccount(
            LeadConvertRequest request, UserPrincipal principal, UUID ownerId, Lead lead) {
        if (request.useExistingAccountId() != null) {
            return accountService.requireAccessibleAccount(request.useExistingAccountId(), principal);
        }
        String name = trimToNull(request.accountName());
        if (name == null) {
            name = trimToNull(lead.getCompany());
        }
        if (name == null) {
            throw new BadRequestException("Account name is required when not using an existing account");
        }
        AccountResponse created = accountService.create(
                new AccountCreateRequest(
                        name,
                        trimToNull(request.accountWebsite()),
                        trimToNull(request.accountPhone()),
                        trimToNull(request.accountIndustry()),
                        null,
                        ownerId),
                principal);
        return accountService.requireAccessibleAccount(created.id(), principal);
    }

    private Contact resolveConversionContact(
            LeadConvertRequest request,
            UserPrincipal principal,
            UUID ownerId,
            Account account,
            Lead lead) {
        if (request.useExistingContactId() != null) {
            Contact contact = contactService.requireAccessibleContact(request.useExistingContactId(), principal);
            if (contact.getAccount() != null && !contact.getAccount().getId().equals(account.getId())) {
                throw new BadRequestException("Existing contact must belong to the selected account");
            }
            return contact;
        }
        String[] names = splitPersonName(request.contactFirstName(), request.contactLastName(), lead.getFullName());
        ContactResponse created = contactService.create(
                new ContactCreateRequest(
                        names[0],
                        names[1],
                        firstNonBlank(request.contactEmail(), lead.getEmail()),
                        firstNonBlank(request.contactPhone(), lead.getPhone()),
                        trimToNull(request.contactJobTitle()),
                        null,
                        account.getId(),
                        ownerId),
                principal);
        return contactService.requireAccessibleContact(created.id(), principal);
    }

    private String[] splitPersonName(String firstName, String lastName, String fullName) {
        String first = trimToNull(firstName);
        String last = trimToNull(lastName);
        if (first != null && last != null) {
            return new String[] {first, last};
        }
        String source = first != null ? first : (last != null ? last : trimToNull(fullName));
        if (source == null) {
            throw new BadRequestException("Contact name is required when not using an existing contact");
        }
        int space = source.indexOf(' ');
        if (space < 0) {
            return new String[] {source, source};
        }
        String parsedFirst = source.substring(0, space).trim();
        String parsedLast = source.substring(space + 1).trim();
        if (parsedFirst.isEmpty()) {
            parsedFirst = source;
        }
        if (parsedLast.isEmpty()) {
            parsedLast = parsedFirst;
        }
        if (first != null) {
            parsedFirst = first;
        }
        if (last != null) {
            parsedLast = last;
        }
        return new String[] {parsedFirst, parsedLast};
    }

    private String firstNonBlank(String primary, String fallback) {
        String value = trimToNull(primary);
        return value != null ? value : trimToNull(fallback);
    }

    private String normalizeOptionalEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
