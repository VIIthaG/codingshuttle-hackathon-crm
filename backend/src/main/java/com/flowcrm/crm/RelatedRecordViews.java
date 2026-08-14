package com.flowcrm.crm;

import com.flowcrm.account.Account;
import com.flowcrm.contact.Contact;
import com.flowcrm.deal.Deal;
import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.lead.Lead;
import java.util.UUID;

public final class RelatedRecordViews {

    public record Snapshot(
            RelatedRecordType type,
            UUID relatedId,
            String relatedName,
            UUID leadId,
            String leadName,
            UUID accountId,
            String accountName,
            UUID contactId,
            String contactName,
            UUID dealId,
            String dealName) {}

    private RelatedRecordViews() {}

    public static Snapshot of(RelatedRecordTarget target) {
        RelatedRecordType type = target.relatedType();
        Lead lead = target.getLead();
        Account account = target.getAccount();
        Contact contact = target.getContact();
        Deal deal = target.getDeal();
        String contactName = contact == null ? null : (contact.getFirstName() + " " + contact.getLastName()).trim();
        return new Snapshot(
                type,
                target.relatedId(),
                target.relatedName(),
                lead == null ? null : lead.getId(),
                lead == null ? null : lead.getFullName(),
                account == null ? null : account.getId(),
                account == null ? null : account.getName(),
                contact == null ? null : contact.getId(),
                contactName,
                deal == null ? null : deal.getId(),
                deal == null ? null : deal.getName());
    }
}
