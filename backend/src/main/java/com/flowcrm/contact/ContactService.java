package com.flowcrm.contact;

import com.flowcrm.account.Account;
import com.flowcrm.account.AccountService;
import com.flowcrm.common.exception.ForbiddenException;
import com.flowcrm.common.exception.ResourceNotFoundException;
import com.flowcrm.contact.dto.ContactCreateRequest;
import com.flowcrm.contact.dto.ContactResponse;
import com.flowcrm.contact.dto.ContactUpdateRequest;
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
public class ContactService {

    private final ContactRepository contactRepository;
    private final AccountService accountService;
    private final UserRepository userRepository;
    private final IdempotencyService idempotencyService;
    private final ContactService self;

    public ContactService(
            ContactRepository contactRepository,
            AccountService accountService,
            UserRepository userRepository,
            IdempotencyService idempotencyService,
            @Lazy ContactService self) {
        this.contactRepository = contactRepository;
        this.accountService = accountService;
        this.userRepository = userRepository;
        this.idempotencyService = idempotencyService;
        this.self = self;
    }

    @Transactional
    public ContactResponse create(ContactCreateRequest request, UserPrincipal principal) {
        User owner = resolveOwnerForCreate(request.ownerId(), principal);
        Account account = resolveAccount(request.accountId(), principal);

        Contact contact = new Contact();
        contact.setFirstName(request.firstName().trim());
        contact.setLastName(request.lastName().trim());
        contact.setEmail(normalizeOptionalEmail(request.email()));
        contact.setPhone(trimToNull(request.phone()));
        contact.setJobTitle(trimToNull(request.jobTitle()));
        contact.setNotes(trimToNull(request.notes()));
        contact.setAccount(account);
        contact.setOwner(owner);

        return toResponse(contactRepository.save(contact));
    }

    public ContactResponse create(ContactCreateRequest request, UserPrincipal principal, String idempotencyKey) {
        return idempotencyService.execute(
                principal.getId(),
                IdempotencyOperations.CONTACTS_CREATE,
                idempotencyKey,
                request,
                ContactResponse.class,
                HttpStatus.CREATED.value(),
                () -> self.create(request, principal));
    }

    @Transactional(readOnly = true)
    public Page<ContactResponse> list(
            String search, UUID accountId, UUID ownerId, UserPrincipal principal, Pageable pageable) {
        Specification<Contact> spec = buildListSpec(search, accountId, ownerId, principal);
        return contactRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ContactResponse getById(UUID id, UserPrincipal principal) {
        Contact contact = requireContact(id);
        assertCanAccess(contact, principal);
        return toResponse(contact);
    }

    @Transactional
    public ContactResponse update(UUID id, ContactUpdateRequest request, UserPrincipal principal) {
        Contact contact = requireContact(id);
        assertCanAccess(contact, principal);

        User owner = resolveOwnerForUpdate(request.ownerId(), principal, contact);
        Account account = resolveAccount(request.accountId(), principal);

        contact.setFirstName(request.firstName().trim());
        contact.setLastName(request.lastName().trim());
        contact.setEmail(normalizeOptionalEmail(request.email()));
        contact.setPhone(trimToNull(request.phone()));
        contact.setJobTitle(trimToNull(request.jobTitle()));
        contact.setNotes(trimToNull(request.notes()));
        contact.setAccount(account);
        contact.setOwner(owner);

        return toResponse(contactRepository.save(contact));
    }

    /**
     * Loads a contact and enforces role-aware visibility. Used by deals.
     */
    @Transactional(readOnly = true)
    public Contact requireAccessibleContact(UUID id, UserPrincipal principal) {
        Contact contact = requireContact(id);
        assertCanAccess(contact, principal);
        return contact;
    }

    @Transactional
    public void delete(UUID id, UserPrincipal principal) {
        Contact contact = requireContact(id);
        assertCanAccess(contact, principal);
        contactRepository.delete(contact);
    }

    private Specification<Contact> buildListSpec(
            String search, UUID accountId, UUID ownerId, UserPrincipal principal) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (principal.getRole() != Role.ADMIN) {
                predicates.add(cb.equal(root.get("owner").get("id"), principal.getId()));
            } else if (ownerId != null) {
                predicates.add(cb.equal(root.get("owner").get("id"), ownerId));
            }

            if (accountId != null) {
                predicates.add(cb.equal(root.get("account").get("id"), accountId));
            }

            String term = search == null ? "" : search.trim().toLowerCase();
            if (!term.isEmpty()) {
                String like = "%" + term + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("jobTitle"), "")), like)));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Account resolveAccount(UUID accountId, UserPrincipal principal) {
        if (accountId == null) {
            return null;
        }
        return accountService.requireAccessibleAccount(accountId, principal);
    }

    private User resolveOwnerForCreate(UUID ownerId, UserPrincipal principal) {
        if (ownerId == null) {
            return requireUser(principal.getId());
        }
        if (!ownerId.equals(principal.getId()) && principal.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only admins can assign contacts to other users");
        }
        return requireUser(ownerId);
    }

    private User resolveOwnerForUpdate(UUID ownerId, UserPrincipal principal, Contact existing) {
        if (ownerId.equals(existing.getOwner().getId())) {
            return existing.getOwner();
        }
        if (principal.getRole() != Role.ADMIN && !ownerId.equals(principal.getId())) {
            throw new ForbiddenException("Only admins can reassign contacts to other users");
        }
        return requireUser(ownerId);
    }

    private void assertCanAccess(Contact contact, UserPrincipal principal) {
        if (principal.getRole() == Role.ADMIN) {
            return;
        }
        if (!contact.getOwner().getId().equals(principal.getId())) {
            throw new ForbiddenException("You do not have access to this contact");
        }
    }

    private Contact requireContact(UUID id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found: " + id));
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private ContactResponse toResponse(Contact contact) {
        User owner = contact.getOwner();
        Account account = contact.getAccount();
        return new ContactResponse(
                contact.getId(),
                contact.getFirstName(),
                contact.getLastName(),
                contact.getEmail(),
                contact.getPhone(),
                contact.getJobTitle(),
                contact.getNotes(),
                account == null ? null : account.getId(),
                account == null ? null : account.getName(),
                owner.getId(),
                owner.getFullName(),
                contact.getCreatedAt(),
                contact.getUpdatedAt());
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
