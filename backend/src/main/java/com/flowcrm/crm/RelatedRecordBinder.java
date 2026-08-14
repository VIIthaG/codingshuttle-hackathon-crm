package com.flowcrm.crm;

import com.flowcrm.account.Account;
import com.flowcrm.account.AccountService;
import com.flowcrm.common.exception.BadRequestException;
import com.flowcrm.contact.Contact;
import com.flowcrm.contact.ContactService;
import com.flowcrm.deal.Deal;
import com.flowcrm.deal.DealService;
import com.flowcrm.lead.Lead;
import com.flowcrm.lead.LeadService;
import com.flowcrm.security.UserPrincipal;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RelatedRecordBinder {

    private final LeadService leadService;
    private final AccountService accountService;
    private final ContactService contactService;
    private final DealService dealService;

    public RelatedRecordBinder(
            LeadService leadService,
            AccountService accountService,
            ContactService contactService,
            DealService dealService) {
        this.leadService = leadService;
        this.accountService = accountService;
        this.contactService = contactService;
        this.dealService = dealService;
    }

    public void bind(
            RelatedRecordTarget target,
            UUID leadId,
            UUID accountId,
            UUID contactId,
            UUID dealId,
            UserPrincipal principal) {
        int count = 0;
        if (leadId != null) {
            count++;
        }
        if (accountId != null) {
            count++;
        }
        if (contactId != null) {
            count++;
        }
        if (dealId != null) {
            count++;
        }
        if (count != 1) {
            throw new BadRequestException("Exactly one of leadId, accountId, contactId, or dealId is required");
        }

        target.clearRelations();
        if (leadId != null) {
            Lead lead = leadService.requireAccessibleLead(leadId, principal);
            target.setLead(lead);
        } else if (accountId != null) {
            Account account = accountService.requireAccessibleAccount(accountId, principal);
            target.setAccount(account);
        } else if (contactId != null) {
            Contact contact = contactService.requireAccessibleContact(contactId, principal);
            target.setContact(contact);
        } else {
            Deal deal = dealService.requireAccessibleDeal(dealId, principal);
            target.setDeal(deal);
        }
    }
}
