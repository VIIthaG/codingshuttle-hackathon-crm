package com.flowcrm.account;

import com.flowcrm.account.dto.AccountCreateRequest;
import com.flowcrm.account.dto.AccountResponse;
import com.flowcrm.account.dto.AccountUpdateRequest;
import com.flowcrm.common.exception.ConflictException;
import com.flowcrm.common.exception.ForbiddenException;
import com.flowcrm.common.exception.ResourceNotFoundException;
import com.flowcrm.contact.ContactRepository;
import com.flowcrm.deal.DealRepository;
import com.flowcrm.lead.LeadRepository;
import com.flowcrm.task.TaskRepository;
import com.flowcrm.enums.Role;
import com.flowcrm.idempotency.IdempotencyOperations;
import com.flowcrm.idempotency.IdempotencyService;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.user.User;
import com.flowcrm.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final DealRepository dealRepository;
    private final LeadRepository leadRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final IdempotencyService idempotencyService;
    private final AccountService self;

    public AccountService(
            AccountRepository accountRepository,
            ContactRepository contactRepository,
            DealRepository dealRepository,
            LeadRepository leadRepository,
            TaskRepository taskRepository,
            UserRepository userRepository,
            IdempotencyService idempotencyService,
            @Lazy AccountService self) {
        this.accountRepository = accountRepository;
        this.contactRepository = contactRepository;
        this.dealRepository = dealRepository;
        this.leadRepository = leadRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.idempotencyService = idempotencyService;
        this.self = self;
    }

    @Transactional
    public AccountResponse create(AccountCreateRequest request, UserPrincipal principal) {
        User owner = resolveOwnerForCreate(request.ownerId(), principal);

        Account account = new Account();
        account.setName(request.name().trim());
        account.setWebsite(trimToNull(request.website()));
        account.setPhone(trimToNull(request.phone()));
        account.setIndustry(trimToNull(request.industry()));
        account.setDescription(trimToNull(request.description()));
        account.setOwner(owner);

        return toResponse(accountRepository.save(account));
    }

    public AccountResponse create(AccountCreateRequest request, UserPrincipal principal, String idempotencyKey) {
        return idempotencyService.execute(
                principal.getId(),
                IdempotencyOperations.ACCOUNTS_CREATE,
                idempotencyKey,
                request,
                AccountResponse.class,
                HttpStatus.CREATED.value(),
                () -> self.create(request, principal));
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> list(String search, UUID ownerId, UserPrincipal principal, Pageable pageable) {
        Specification<Account> spec = buildListSpec(search, ownerId, principal);
        return accountRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AccountResponse getById(UUID id, UserPrincipal principal) {
        Account account = requireAccount(id);
        assertCanAccess(account, principal);
        return toResponse(account);
    }

    @Transactional
    public AccountResponse update(UUID id, AccountUpdateRequest request, UserPrincipal principal) {
        Account account = requireAccount(id);
        assertCanAccess(account, principal);

        User owner = resolveOwnerForUpdate(request.ownerId(), principal, account);
        account.setName(request.name().trim());
        account.setWebsite(trimToNull(request.website()));
        account.setPhone(trimToNull(request.phone()));
        account.setIndustry(trimToNull(request.industry()));
        account.setDescription(trimToNull(request.description()));
        account.setOwner(owner);

        return toResponse(accountRepository.save(account));
    }

    @Transactional
    public void delete(UUID id, UserPrincipal principal) {
        Account account = requireAccount(id);
        assertCanAccess(account, principal);
        if (dealRepository.existsByAccountId(id)) {
            throw new ConflictException("Cannot delete account while deals still reference it");
        }
        if (leadRepository.existsByConvertedAccountId(id)) {
            throw new ConflictException("Cannot delete account while converted leads still reference it");
        }
        if (taskRepository.existsByAccount_Id(id)) {
            throw new ConflictException("Cannot delete account while tasks still reference it");
        }
        accountRepository.delete(account);
    }

    /**
     * Loads an account and enforces role-aware visibility. Used by contacts.
     */
    @Transactional(readOnly = true)
    public Account requireAccessibleAccount(UUID id, UserPrincipal principal) {
        Account account = requireAccount(id);
        assertCanAccess(account, principal);
        return account;
    }

    private Specification<Account> buildListSpec(String search, UUID ownerId, UserPrincipal principal) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (principal.getRole() != Role.ADMIN) {
                predicates.add(cb.equal(root.get("owner").get("id"), principal.getId()));
            } else if (ownerId != null) {
                predicates.add(cb.equal(root.get("owner").get("id"), ownerId));
            }

            String term = search == null ? "" : search.trim().toLowerCase();
            if (!term.isEmpty()) {
                String like = "%" + term + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("website"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("industry"), "")), like)));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private User resolveOwnerForCreate(UUID ownerId, UserPrincipal principal) {
        if (ownerId == null) {
            return requireUser(principal.getId());
        }
        if (!ownerId.equals(principal.getId()) && principal.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only admins can assign accounts to other users");
        }
        return requireUser(ownerId);
    }

    private User resolveOwnerForUpdate(UUID ownerId, UserPrincipal principal, Account existing) {
        if (ownerId.equals(existing.getOwner().getId())) {
            return existing.getOwner();
        }
        if (principal.getRole() != Role.ADMIN && !ownerId.equals(principal.getId())) {
            throw new ForbiddenException("Only admins can reassign accounts to other users");
        }
        return requireUser(ownerId);
    }

    private void assertCanAccess(Account account, UserPrincipal principal) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (!account.getOwner().getId().equals(principal.getId())) {
            throw new ForbiddenException("You do not have access to this account");
        }
    }

    private Account requireAccount(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private AccountResponse toResponse(Account account) {
        User owner = account.getOwner();
        long contactCount = contactRepository.countByAccountId(account.getId());
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getWebsite(),
                account.getPhone(),
                account.getIndustry(),
                account.getDescription(),
                owner.getId(),
                owner.getFullName(),
                contactCount,
                account.getCreatedAt(),
                account.getUpdatedAt());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
