package com.flowcrm.deal;

import com.flowcrm.account.Account;
import com.flowcrm.account.AccountService;
import com.flowcrm.common.exception.BadRequestException;
import com.flowcrm.common.exception.ForbiddenException;
import com.flowcrm.common.exception.InvalidStatusTransitionException;
import com.flowcrm.common.exception.ResourceNotFoundException;
import com.flowcrm.contact.Contact;
import com.flowcrm.contact.ContactService;
import com.flowcrm.dashboard.DashboardService;
import com.flowcrm.deal.dto.DealCreateRequest;
import com.flowcrm.deal.dto.DealResponse;
import com.flowcrm.deal.dto.DealStageUpdateRequest;
import com.flowcrm.deal.dto.DealUpdateRequest;
import com.flowcrm.enums.DealStage;
import com.flowcrm.enums.Role;
import com.flowcrm.idempotency.IdempotencyOperations;
import com.flowcrm.idempotency.IdempotencyService;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.user.User;
import com.flowcrm.user.UserRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DealService {

    private final DealRepository dealRepository;
    private final AccountService accountService;
    private final ContactService contactService;
    private final UserRepository userRepository;
    private final DashboardService dashboardService;
    private final IdempotencyService idempotencyService;
    private final DealService self;

    public DealService(
            DealRepository dealRepository,
            AccountService accountService,
            ContactService contactService,
            UserRepository userRepository,
            DashboardService dashboardService,
            IdempotencyService idempotencyService,
            @Lazy DealService self) {
        this.dealRepository = dealRepository;
        this.accountService = accountService;
        this.contactService = contactService;
        this.userRepository = userRepository;
        this.dashboardService = dashboardService;
        this.idempotencyService = idempotencyService;
        this.self = self;
    }

    @Transactional
    public DealResponse create(DealCreateRequest request, UserPrincipal principal) {
        User owner = resolveOwnerForCreate(request.ownerId(), principal);
        Account account = accountService.requireAccessibleAccount(request.accountId(), principal);
        Contact contact = resolveContact(request.primaryContactId(), principal, account);

        DealStage stage = request.stage() != null ? request.stage() : DealStage.PROSPECTING;

        Deal deal = new Deal();
        deal.setName(request.name().trim());
        deal.setAccount(account);
        deal.setPrimaryContact(contact);
        deal.setOwner(owner);
        deal.setStage(stage);
        deal.setAmount(request.amount());
        deal.setCurrency(normalizeCurrency(request.currency()));
        deal.setProbability(resolveProbability(stage, request.probability(), true));
        deal.setExpectedCloseDate(request.expectedCloseDate());
        deal.setDescription(trimToNull(request.description()));
        deal.setLostReason(stage == DealStage.CLOSED_LOST ? trimToNull(request.lostReason()) : null);

        DealResponse response = toResponse(dealRepository.save(deal));
        dashboardService.invalidateAllSummaries();
        return response;
    }

    public DealResponse create(DealCreateRequest request, UserPrincipal principal, String idempotencyKey) {
        return idempotencyService.execute(
                principal.getId(),
                IdempotencyOperations.DEALS_CREATE,
                idempotencyKey,
                request,
                DealResponse.class,
                HttpStatus.CREATED.value(),
                () -> self.create(request, principal));
    }

    @Transactional(readOnly = true)
    public Page<DealResponse> list(
            String search,
            DealStage stage,
            UUID accountId,
            UUID ownerId,
            LocalDate expectedCloseFrom,
            LocalDate expectedCloseTo,
            UserPrincipal principal,
            Pageable pageable) {
        Specification<Deal> spec =
                buildListSpec(search, stage, accountId, ownerId, expectedCloseFrom, expectedCloseTo, principal);
        return dealRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DealResponse getById(UUID id, UserPrincipal principal) {
        Deal deal = requireDeal(id);
        assertCanAccess(deal, principal);
        return toResponse(deal);
    }

    @Transactional
    public DealResponse update(UUID id, DealUpdateRequest request, UserPrincipal principal) {
        Deal deal = requireDeal(id);
        assertCanAccess(deal, principal);

        User owner = resolveOwnerForUpdate(request.ownerId(), principal, deal);
        Account account = accountService.requireAccessibleAccount(request.accountId(), principal);
        Contact contact = resolveContact(request.primaryContactId(), principal, account);

        applyStageChange(deal, request.stage(), request.lostReason(), request.probability(), false);

        deal.setName(request.name().trim());
        deal.setAccount(account);
        deal.setPrimaryContact(contact);
        deal.setOwner(owner);
        deal.setAmount(request.amount());
        deal.setCurrency(normalizeCurrency(request.currency()));
        deal.setExpectedCloseDate(request.expectedCloseDate());
        deal.setDescription(trimToNull(request.description()));
        if (deal.getStage() == DealStage.CLOSED_LOST) {
            String reason = trimToNull(request.lostReason());
            if (reason != null) {
                deal.setLostReason(reason);
            }
        }

        DealResponse response = toResponse(dealRepository.save(deal));
        dashboardService.invalidateAllSummaries();
        return response;
    }

    @Transactional
    public DealResponse changeStage(UUID id, DealStageUpdateRequest request, UserPrincipal principal) {
        Deal deal = requireDeal(id);
        assertCanAccess(deal, principal);
        applyStageChange(deal, request.stage(), request.lostReason(), request.probability(), true);
        DealResponse response = toResponse(dealRepository.save(deal));
        dashboardService.invalidateAllSummaries();
        return response;
    }

    @Transactional
    public void delete(UUID id, UserPrincipal principal) {
        Deal deal = requireDeal(id);
        assertCanAccess(deal, principal);
        dealRepository.delete(deal);
        dashboardService.invalidateAllSummaries();
    }

    private void applyStageChange(
            Deal deal, DealStage target, String lostReason, Integer probabilityOverride, boolean requireChange) {
        DealStage current = deal.getStage();
        if (current == target) {
            if (requireChange) {
                throw new InvalidStatusTransitionException("Deal is already in stage " + current);
            }
            if (DealStageTransitions.isTerminal(current)) {
                deal.setProbability(DealStageTransitions.defaultProbability(current));
            } else if (probabilityOverride != null) {
                deal.setProbability(probabilityOverride);
            }
            return;
        }
        if (!DealStageTransitions.canTransition(current, target)) {
            throw new InvalidStatusTransitionException(
                    "Cannot transition deal stage from " + current + " to " + target);
        }
        deal.setStage(target);
        deal.setProbability(resolveProbability(target, probabilityOverride, true));
        if (target == DealStage.CLOSED_LOST) {
            String reason = trimToNull(lostReason);
            if (reason != null) {
                deal.setLostReason(reason);
            }
        } else {
            deal.setLostReason(null);
        }
    }

    private int resolveProbability(DealStage stage, Integer override, boolean advancing) {
        if (stage == DealStage.CLOSED_WON) {
            return 100;
        }
        if (stage == DealStage.CLOSED_LOST) {
            return 0;
        }
        if (override != null) {
            return override;
        }
        if (advancing) {
            return DealStageTransitions.defaultProbability(stage);
        }
        return DealStageTransitions.defaultProbability(stage);
    }

    private Specification<Deal> buildListSpec(
            String search,
            DealStage stage,
            UUID accountId,
            UUID ownerId,
            LocalDate expectedCloseFrom,
            LocalDate expectedCloseTo,
            UserPrincipal principal) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();

            if (principal.getRole() != Role.ADMIN) {
                predicates.add(cb.equal(root.get("owner").get("id"), principal.getId()));
            } else if (ownerId != null) {
                predicates.add(cb.equal(root.get("owner").get("id"), ownerId));
            }

            if (stage != null) {
                predicates.add(cb.equal(root.get("stage"), stage));
            }
            if (accountId != null) {
                predicates.add(cb.equal(root.get("account").get("id"), accountId));
            }
            if (expectedCloseFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expectedCloseDate"), expectedCloseFrom));
            }
            if (expectedCloseTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expectedCloseDate"), expectedCloseTo));
            }

            String term = search == null ? "" : search.trim().toLowerCase();
            if (!term.isEmpty()) {
                String like = "%" + term + "%";
                Join<Deal, Account> accountJoin = root.join("account", JoinType.INNER);
                Join<Deal, Contact> contactJoin = root.join("primaryContact", JoinType.LEFT);
                Predicate contactName = cb.like(
                        cb.lower(cb.concat(
                                cb.concat(cb.coalesce(contactJoin.get("firstName"), ""), " "),
                                cb.coalesce(contactJoin.get("lastName"), ""))),
                        like);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(accountJoin.get("name")), like),
                        contactName));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Contact resolveContact(UUID contactId, UserPrincipal principal, Account account) {
        if (contactId == null) {
            return null;
        }
        Contact contact = contactService.requireAccessibleContact(contactId, principal);
        if (contact.getAccount() != null && !contact.getAccount().getId().equals(account.getId())) {
            throw new BadRequestException("Primary contact must belong to the same account as the deal");
        }
        return contact;
    }

    private User resolveOwnerForCreate(UUID ownerId, UserPrincipal principal) {
        if (ownerId == null) {
            return requireUser(principal.getId());
        }
        if (!ownerId.equals(principal.getId()) && principal.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only admins can assign deals to other users");
        }
        return requireUser(ownerId);
    }

    private User resolveOwnerForUpdate(UUID ownerId, UserPrincipal principal, Deal existing) {
        if (ownerId.equals(existing.getOwner().getId())) {
            return existing.getOwner();
        }
        if (principal.getRole() != Role.ADMIN && !ownerId.equals(principal.getId())) {
            throw new ForbiddenException("Only admins can reassign deals to other users");
        }
        return requireUser(ownerId);
    }

    private void assertCanAccess(Deal deal, UserPrincipal principal) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (!deal.getOwner().getId().equals(principal.getId())) {
            throw new ForbiddenException("You do not have access to this deal");
        }
    }

    private Deal requireDeal(UUID id) {
        return dealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal not found: " + id));
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private DealResponse toResponse(Deal deal) {
        Account account = deal.getAccount();
        Contact contact = deal.getPrimaryContact();
        User owner = deal.getOwner();
        String contactName = null;
        if (contact != null) {
            contactName = (contact.getFirstName() + " " + contact.getLastName()).trim();
        }
        return new DealResponse(
                deal.getId(),
                deal.getName(),
                account.getId(),
                account.getName(),
                contact == null ? null : contact.getId(),
                contactName,
                owner.getId(),
                owner.getFullName(),
                deal.getStage(),
                deal.getAmount(),
                deal.getCurrency(),
                deal.getProbability(),
                deal.getExpectedCloseDate(),
                deal.getDescription(),
                deal.getLostReason(),
                deal.getCreatedAt(),
                deal.getUpdatedAt());
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "USD";
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
