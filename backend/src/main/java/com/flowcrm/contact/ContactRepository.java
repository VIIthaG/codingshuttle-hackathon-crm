package com.flowcrm.contact;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ContactRepository extends JpaRepository<Contact, UUID>, JpaSpecificationExecutor<Contact> {

    long countByAccountId(UUID accountId);

    java.util.List<Contact> findTop8ByAccount_IdOrderByLastNameAsc(UUID accountId);
}
