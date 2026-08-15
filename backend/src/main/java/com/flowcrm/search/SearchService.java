package com.flowcrm.search;

import com.flowcrm.account.Account;
import com.flowcrm.account.AccountRepository;
import com.flowcrm.call.Call;
import com.flowcrm.call.CallRepository;
import com.flowcrm.common.exception.BadRequestException;
import com.flowcrm.contact.Contact;
import com.flowcrm.contact.ContactRepository;
import com.flowcrm.crm.RelatedRecordViews;
import com.flowcrm.deal.Deal;
import com.flowcrm.deal.DealRepository;
import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.enums.Role;
import com.flowcrm.enums.SearchResultType;
import com.flowcrm.lead.Lead;
import com.flowcrm.lead.LeadRepository;
import com.flowcrm.meeting.Meeting;
import com.flowcrm.meeting.MeetingRepository;
import com.flowcrm.search.dto.SearchResponse;
import com.flowcrm.search.dto.SearchResultResponse;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.task.Task;
import com.flowcrm.task.TaskRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchService {

    public static final int MIN_QUERY_LENGTH = 2;
    public static final int DEFAULT_LIMIT = 24;
    public static final int MAX_LIMIT = 50;
    public static final int PER_TYPE_LIMIT = 8;

    private final LeadRepository leadRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final DealRepository dealRepository;
    private final TaskRepository taskRepository;
    private final MeetingRepository meetingRepository;
    private final CallRepository callRepository;

    public SearchService(
            LeadRepository leadRepository,
            AccountRepository accountRepository,
            ContactRepository contactRepository,
            DealRepository dealRepository,
            TaskRepository taskRepository,
            MeetingRepository meetingRepository,
            CallRepository callRepository) {
        this.leadRepository = leadRepository;
        this.accountRepository = accountRepository;
        this.contactRepository = contactRepository;
        this.dealRepository = dealRepository;
        this.taskRepository = taskRepository;
        this.meetingRepository = meetingRepository;
        this.callRepository = callRepository;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(String rawQuery, String typesParam, Integer limitParam, UserPrincipal principal) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        int limit = resolveLimit(limitParam);
        if (query.length() < MIN_QUERY_LENGTH) {
            return new SearchResponse(query, List.of());
        }
        String needle = sanitize(query).toLowerCase(Locale.ROOT);
        if (needle.length() < MIN_QUERY_LENGTH) {
            return new SearchResponse(query, List.of());
        }
        Set<SearchResultType> types = parseTypes(typesParam);
        int perType = types.size() == 1 ? limit : Math.min(PER_TYPE_LIMIT, limit);
        PageRequest page = PageRequest.of(0, perType);

        List<Ranked> ranked = new ArrayList<>();
        if (types.contains(SearchResultType.LEAD)) {
            ranked.addAll(searchLeads(needle, principal, page));
        }
        if (types.contains(SearchResultType.ACCOUNT)) {
            ranked.addAll(searchAccounts(needle, principal, page));
        }
        if (types.contains(SearchResultType.CONTACT)) {
            ranked.addAll(searchContacts(needle, principal, page));
        }
        if (types.contains(SearchResultType.DEAL)) {
            ranked.addAll(searchDeals(needle, principal, page));
        }
        if (types.contains(SearchResultType.TASK)) {
            ranked.addAll(searchTasks(needle, principal, page));
        }
        if (types.contains(SearchResultType.MEETING)) {
            ranked.addAll(searchMeetings(needle, principal, page));
        }
        if (types.contains(SearchResultType.CALL)) {
            ranked.addAll(searchCalls(needle, principal, page));
        }

        ranked.sort(Comparator.comparingInt(Ranked::rank)
                .thenComparing(r -> r.result().type().ordinal())
                .thenComparing(r -> r.result().title(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(r -> r.result().id()));

        List<SearchResultResponse> results = ranked.stream().limit(limit).map(Ranked::result).toList();
        return new SearchResponse(query, results);
    }

    private List<Ranked> searchLeads(String needle, UserPrincipal principal, PageRequest page) {
        Specification<Lead> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            scopeOwner(predicates, cb, root.get("assignedTo").get("id"), principal);
            predicates.add(cb.or(
                    contains(cb, root.get("fullName"), needle),
                    contains(cb, root.get("company"), needle),
                    contains(cb, root.get("email"), needle),
                    contains(cb, root.get("phone"), needle)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        List<Ranked> out = new ArrayList<>();
        for (Lead lead : leadRepository.findAll(spec, page)) {
            out.add(ranked(
                    SearchResultType.LEAD,
                    lead.getId(),
                    lead.getFullName(),
                    firstNonBlank(lead.getCompany(), lead.getEmail(), lead.getPhone()),
                    lead.getStatus().name(),
                    null,
                    null,
                    null,
                    Map.of(),
                    needle,
                    lead.getFullName(),
                    lead.getCompany(),
                    lead.getEmail(),
                    lead.getPhone()));
        }
        return out;
    }

    private List<Ranked> searchAccounts(String needle, UserPrincipal principal, PageRequest page) {
        Specification<Account> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            scopeOwner(predicates, cb, root.get("owner").get("id"), principal);
            predicates.add(cb.or(
                    contains(cb, root.get("name"), needle),
                    contains(cb, root.get("industry"), needle),
                    contains(cb, root.get("website"), needle),
                    contains(cb, root.get("phone"), needle)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        List<Ranked> out = new ArrayList<>();
        for (Account account : accountRepository.findAll(spec, page)) {
            out.add(ranked(
                    SearchResultType.ACCOUNT,
                    account.getId(),
                    account.getName(),
                    firstNonBlank(account.getIndustry(), account.getWebsite(), account.getPhone()),
                    null,
                    null,
                    null,
                    null,
                    Map.of(),
                    needle,
                    account.getName(),
                    account.getIndustry(),
                    account.getWebsite(),
                    account.getPhone()));
        }
        return out;
    }

    private List<Ranked> searchContacts(String needle, UserPrincipal principal, PageRequest page) {
        Specification<Contact> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            scopeOwner(predicates, cb, root.get("owner").get("id"), principal);
            var fullName = cb.lower(cb.concat(cb.concat(cb.coalesce(root.get("firstName"), ""), " "), cb.coalesce(root.get("lastName"), "")));
            predicates.add(cb.or(
                    cb.like(fullName, "%" + needle + "%"),
                    contains(cb, root.get("firstName"), needle),
                    contains(cb, root.get("lastName"), needle),
                    contains(cb, root.get("email"), needle),
                    contains(cb, root.get("phone"), needle),
                    contains(cb, root.get("jobTitle"), needle)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        List<Ranked> out = new ArrayList<>();
        for (Contact contact : contactRepository.findAll(spec, page)) {
            String name = (contact.getFirstName() + " " + contact.getLastName()).trim();
            String accountName = contact.getAccount() == null ? null : contact.getAccount().getName();
            out.add(ranked(
                    SearchResultType.CONTACT,
                    contact.getId(),
                    name,
                    firstNonBlank(contact.getJobTitle(), accountName, contact.getEmail(), contact.getPhone()),
                    null,
                    contact.getAccount() == null ? null : RelatedRecordType.ACCOUNT,
                    contact.getAccount() == null ? null : contact.getAccount().getId(),
                    accountName,
                    Map.of(),
                    needle,
                    name,
                    contact.getFirstName(),
                    contact.getLastName(),
                    contact.getEmail(),
                    contact.getPhone(),
                    contact.getJobTitle()));
        }
        return out;
    }

    private List<Ranked> searchDeals(String needle, UserPrincipal principal, PageRequest page) {
        Specification<Deal> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            scopeOwner(predicates, cb, root.get("owner").get("id"), principal);
            var account = root.join("account", JoinType.INNER);
            var contact = root.join("primaryContact", JoinType.LEFT);
            var contactName = cb.lower(cb.concat(
                    cb.concat(cb.coalesce(contact.get("firstName"), ""), " "), cb.coalesce(contact.get("lastName"), "")));
            predicates.add(cb.or(
                    contains(cb, root.get("name"), needle),
                    contains(cb, account.get("name"), needle),
                    cb.like(contactName, "%" + needle + "%")));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        List<Ranked> out = new ArrayList<>();
        for (Deal deal : dealRepository.findAll(spec, page)) {
            String accountName = deal.getAccount().getName();
            String contactName = deal.getPrimaryContact() == null
                    ? null
                    : (deal.getPrimaryContact().getFirstName() + " " + deal.getPrimaryContact().getLastName()).trim();
            out.add(ranked(
                    SearchResultType.DEAL,
                    deal.getId(),
                    deal.getName(),
                    firstNonBlank(accountName, contactName),
                    deal.getStage().name(),
                    RelatedRecordType.ACCOUNT,
                    deal.getAccount().getId(),
                    accountName,
                    Map.of(),
                    needle,
                    deal.getName(),
                    accountName,
                    contactName));
        }
        return out;
    }

    private List<Ranked> searchTasks(String needle, UserPrincipal principal, PageRequest page) {
        Specification<Task> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            scopeOwner(predicates, cb, root.get("assignedTo").get("id"), principal);
            predicates.add(cb.or(contains(cb, root.get("title"), needle), contains(cb, root.get("description"), needle)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        List<Ranked> out = new ArrayList<>();
        for (Task task : taskRepository.findAll(spec, page)) {
            out.add(ranked(
                    SearchResultType.TASK,
                    task.getId(),
                    task.getTitle(),
                    relatedSubtitle(task.relatedType(), task.relatedName()),
                    task.getStatus().name(),
                    task.relatedType(),
                    task.relatedId(),
                    task.relatedName(),
                    Map.of(),
                    needle,
                    task.getTitle(),
                    task.getDescription()));
        }
        return out;
    }

    private List<Ranked> searchMeetings(String needle, UserPrincipal principal, PageRequest page) {
        Specification<Meeting> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            scopeOwner(predicates, cb, root.get("assignedTo").get("id"), principal);
            predicates.add(cb.or(
                    contains(cb, root.get("title"), needle),
                    contains(cb, root.get("description"), needle),
                    contains(cb, root.get("location"), needle)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        List<Ranked> out = new ArrayList<>();
        for (Meeting meeting : meetingRepository.findAll(spec, page)) {
            RelatedRecordViews.Snapshot rel = RelatedRecordViews.of(meeting);
            out.add(ranked(
                    SearchResultType.MEETING,
                    meeting.getId(),
                    meeting.getTitle(),
                    relatedSubtitle(rel.type(), rel.relatedName()),
                    meeting.getStatus().name(),
                    rel.type(),
                    rel.relatedId(),
                    rel.relatedName(),
                    Map.of(),
                    needle,
                    meeting.getTitle(),
                    meeting.getDescription(),
                    meeting.getLocation()));
        }
        return out;
    }

    private List<Ranked> searchCalls(String needle, UserPrincipal principal, PageRequest page) {
        Specification<Call> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            scopeOwner(predicates, cb, root.get("assignedTo").get("id"), principal);
            predicates.add(cb.or(
                    contains(cb, root.get("title"), needle),
                    contains(cb, root.get("description"), needle),
                    contains(cb, root.get("phoneNumber"), needle),
                    contains(cb, root.get("outcome"), needle)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        List<Ranked> out = new ArrayList<>();
        for (Call call : callRepository.findAll(spec, page)) {
            RelatedRecordViews.Snapshot rel = RelatedRecordViews.of(call);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("direction", call.getDirection().name());
            out.add(ranked(
                    SearchResultType.CALL,
                    call.getId(),
                    call.getTitle(),
                    relatedSubtitle(rel.type(), rel.relatedName()),
                    call.getStatus().name(),
                    rel.type(),
                    rel.relatedId(),
                    rel.relatedName(),
                    meta,
                    needle,
                    call.getTitle(),
                    call.getDescription(),
                    call.getPhoneNumber(),
                    call.getOutcome()));
        }
        return out;
    }

    private static void scopeOwner(
            List<Predicate> predicates,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Path<UUID> ownerPath,
            UserPrincipal principal) {
        if (principal.getRole() != Role.ADMIN) {
            predicates.add(cb.equal(ownerPath, principal.getId()));
        }
    }

    private static Predicate contains(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Path<String> path,
            String needle) {
        return cb.like(cb.lower(cb.coalesce(path, "")), "%" + needle + "%");
    }

    private static Ranked ranked(
            SearchResultType type,
            UUID id,
            String title,
            String subtitle,
            String status,
            RelatedRecordType relatedType,
            UUID relatedId,
            String relatedName,
            Map<String, Object> metadata,
            String needle,
            String... rankFields) {
        return new Ranked(
                new SearchResultResponse(type, id, title, subtitle, status, relatedType, relatedId, relatedName, metadata),
                rank(needle, rankFields));
    }

    private static int rank(String needle, String... values) {
        int best = 99;
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String n = value.toLowerCase(Locale.ROOT);
            if (n.equals(needle)) {
                best = Math.min(best, 0);
            } else if (n.startsWith(needle)) {
                best = Math.min(best, 1);
            } else if (n.contains(needle)) {
                best = Math.min(best, 2);
            }
        }
        return best;
    }

    private static String relatedSubtitle(RelatedRecordType type, String relatedName) {
        if (type == null || relatedName == null || relatedName.isBlank()) {
            return null;
        }
        String label =
                switch (type) {
                    case LEAD -> "Lead";
                    case ACCOUNT -> "Account";
                    case CONTACT -> "Contact";
                    case DEAL -> "Deal";
                };
        return label + " · " + relatedName;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static int resolveLimit(Integer limitParam) {
        if (limitParam == null) {
            return DEFAULT_LIMIT;
        }
        if (limitParam < 1) {
            throw new BadRequestException("limit must be at least 1");
        }
        return Math.min(limitParam, MAX_LIMIT);
    }

    private static Set<SearchResultType> parseTypes(String typesParam) {
        if (typesParam == null || typesParam.isBlank()) {
            return EnumSet.allOf(SearchResultType.class);
        }
        EnumSet<SearchResultType> types = EnumSet.noneOf(SearchResultType.class);
        for (String raw : typesParam.split(",")) {
            String token = raw.trim().toUpperCase(Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }
            try {
                types.add(SearchResultType.valueOf(token));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Unknown search type: " + token);
            }
        }
        if (types.isEmpty()) {
            return EnumSet.allOf(SearchResultType.class);
        }
        return types;
    }

    private static String sanitize(String query) {
        return query.replace("%", "").replace("_", "").replace("\\", "");
    }

    private record Ranked(SearchResultResponse result, int rank) {}
}
