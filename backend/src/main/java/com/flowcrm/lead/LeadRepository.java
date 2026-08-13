package com.flowcrm.lead;

import com.flowcrm.enums.LeadStatus;
import com.flowcrm.user.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    Page<Lead> findByAssignedTo(User assignedTo, Pageable pageable);

    Page<Lead> findByStatus(LeadStatus status, Pageable pageable);

    Page<Lead> findByAssignedToAndStatus(User assignedTo, LeadStatus status, Pageable pageable);

    long countByAssignedToId(UUID assignedToId);

    boolean existsByConvertedAccountId(UUID accountId);

    boolean existsByConvertedContactId(UUID contactId);

    boolean existsByConvertedDealId(UUID dealId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lead l WHERE l.id = :id")
    Optional<Lead> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT l.status, COUNT(l) FROM Lead l GROUP BY l.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT l.status, COUNT(l) FROM Lead l WHERE l.assignedTo.id = :assignedToId GROUP BY l.status")
    List<Object[]> countGroupedByStatusForAssignee(@Param("assignedToId") UUID assignedToId);
}
