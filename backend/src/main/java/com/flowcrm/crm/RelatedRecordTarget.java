package com.flowcrm.crm;

import com.flowcrm.account.Account;
import com.flowcrm.contact.Contact;
import com.flowcrm.deal.Deal;
import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.lead.Lead;
import java.util.UUID;

public interface RelatedRecordTarget {

    void clearRelations();

    void setLead(Lead lead);

    void setAccount(Account account);

    void setContact(Contact contact);

    void setDeal(Deal deal);

    Lead getLead();

    Account getAccount();

    Contact getContact();

    Deal getDeal();

    default RelatedRecordType relatedType() {
        if (getLead() != null) {
            return RelatedRecordType.LEAD;
        }
        if (getAccount() != null) {
            return RelatedRecordType.ACCOUNT;
        }
        if (getContact() != null) {
            return RelatedRecordType.CONTACT;
        }
        if (getDeal() != null) {
            return RelatedRecordType.DEAL;
        }
        throw new IllegalStateException("Record has no related CRM entity");
    }

    default UUID relatedId() {
        return switch (relatedType()) {
            case LEAD -> getLead().getId();
            case ACCOUNT -> getAccount().getId();
            case CONTACT -> getContact().getId();
            case DEAL -> getDeal().getId();
        };
    }

    default String relatedName() {
        Contact contact = getContact();
        return switch (relatedType()) {
            case LEAD -> getLead().getFullName();
            case ACCOUNT -> getAccount().getName();
            case CONTACT -> (contact.getFirstName() + " " + contact.getLastName()).trim();
            case DEAL -> getDeal().getName();
        };
    }
}
