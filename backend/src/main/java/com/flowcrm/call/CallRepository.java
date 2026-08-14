package com.flowcrm.call;

import com.flowcrm.enums.CallStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CallRepository extends JpaRepository<Call, UUID>, JpaSpecificationExecutor<Call> {

    boolean existsByLead_Id(UUID leadId);

    boolean existsByAccount_Id(UUID accountId);

    boolean existsByContact_Id(UUID contactId);

    boolean existsByDeal_Id(UUID dealId);

    List<Call> findByLead_IdOrderByCreatedAtDesc(UUID leadId);

    List<Call> findByAccount_IdOrderByCreatedAtDesc(UUID accountId);

    List<Call> findByContact_IdOrderByCreatedAtDesc(UUID contactId);

    List<Call> findByDeal_IdOrderByCreatedAtDesc(UUID dealId);

    List<Call> findByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
            CallStatus status, Instant from, Instant to);

    List<Call> findByAssignedTo_IdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
            UUID assignedToId, CallStatus status, Instant from, Instant to);
}
