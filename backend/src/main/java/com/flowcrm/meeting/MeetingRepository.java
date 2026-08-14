package com.flowcrm.meeting;

import com.flowcrm.enums.MeetingStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MeetingRepository extends JpaRepository<Meeting, UUID>, JpaSpecificationExecutor<Meeting> {

    boolean existsByLead_Id(UUID leadId);

    boolean existsByAccount_Id(UUID accountId);

    boolean existsByContact_Id(UUID contactId);

    boolean existsByDeal_Id(UUID dealId);

    List<Meeting> findByLead_IdOrderByCreatedAtDesc(UUID leadId);

    List<Meeting> findByAccount_IdOrderByCreatedAtDesc(UUID accountId);

    List<Meeting> findByContact_IdOrderByCreatedAtDesc(UUID contactId);

    List<Meeting> findByDeal_IdOrderByCreatedAtDesc(UUID dealId);

    List<Meeting> findByStatusAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
            MeetingStatus status, Instant from, Instant to);

    List<Meeting> findByAssignedTo_IdAndStatusAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
            UUID assignedToId, MeetingStatus status, Instant from, Instant to);
}
