package com.flowcrm.lead;

import com.flowcrm.enums.LeadStatus;
import com.flowcrm.user.User;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    Page<Lead> findByAssignedTo(User assignedTo, Pageable pageable);

    Page<Lead> findByStatus(LeadStatus status, Pageable pageable);

    Page<Lead> findByAssignedToAndStatus(User assignedTo, LeadStatus status, Pageable pageable);
}
