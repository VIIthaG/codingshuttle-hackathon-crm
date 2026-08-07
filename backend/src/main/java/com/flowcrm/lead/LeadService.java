package com.flowcrm.lead;

import com.flowcrm.common.exception.ForbiddenException;
import com.flowcrm.common.exception.InvalidStatusTransitionException;
import com.flowcrm.common.exception.ResourceNotFoundException;
import com.flowcrm.enums.LeadStatus;
import com.flowcrm.enums.Role;
import com.flowcrm.lead.dto.LeadCreateRequest;
import com.flowcrm.lead.dto.LeadResponse;
import com.flowcrm.lead.dto.LeadStatusUpdateRequest;
import com.flowcrm.lead.dto.LeadUpdateRequest;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.user.User;
import com.flowcrm.user.UserRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadService {

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;

    public LeadService(LeadRepository leadRepository, UserRepository userRepository) {
        this.leadRepository = leadRepository;
        this.userRepository = userRepository;
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

        return toResponse(leadRepository.save(lead));
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

        return toResponse(leadRepository.save(lead));
    }

    @Transactional
    public LeadResponse changeStatus(UUID id, LeadStatusUpdateRequest request, UserPrincipal principal) {
        Lead lead = requireLead(id);
        assertCanAccess(lead, principal);
        applyStatusTransition(lead, request.status());
        return toResponse(leadRepository.save(lead));
    }

    @Transactional
    public void delete(UUID id, UserPrincipal principal) {
        Lead lead = requireLead(id);
        assertCanAccess(lead, principal);
        leadRepository.delete(lead);
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
                lead.getUpdatedAt());
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
