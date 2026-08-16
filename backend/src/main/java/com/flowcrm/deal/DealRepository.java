package com.flowcrm.deal;

import com.flowcrm.enums.DealStage;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealRepository extends JpaRepository<Deal, UUID>, JpaSpecificationExecutor<Deal> {

    boolean existsByAccountId(UUID accountId);

    long countByStageNotIn(Collection<DealStage> stages);

    long countByOwnerIdAndStageNotIn(UUID ownerId, Collection<DealStage> stages);

    long countByStage(DealStage stage);

    long countByOwnerIdAndStage(UUID ownerId, DealStage stage);

    @Query("SELECT d.stage, COUNT(d) FROM Deal d GROUP BY d.stage")
    List<Object[]> countGroupedByStage();

    @Query("SELECT d.stage, COUNT(d) FROM Deal d WHERE d.owner.id = :ownerId GROUP BY d.stage")
    List<Object[]> countGroupedByStageForOwner(@Param("ownerId") UUID ownerId);

    @Query("SELECT COALESCE(SUM(COALESCE(d.amount, 0)), 0) FROM Deal d WHERE d.stage NOT IN :stages")
    BigDecimal sumAmountWhereStageNotIn(@Param("stages") Collection<DealStage> stages);

    @Query(
            """
            SELECT COALESCE(SUM(COALESCE(d.amount, 0)), 0)
            FROM Deal d
            WHERE d.owner.id = :ownerId AND d.stage NOT IN :stages
            """)
    BigDecimal sumAmountWhereOwnerAndStageNotIn(
            @Param("ownerId") UUID ownerId, @Param("stages") Collection<DealStage> stages);

    @Query("SELECT COALESCE(SUM(COALESCE(d.amount, 0)), 0) FROM Deal d WHERE d.stage = :stage")
    BigDecimal sumAmountByStage(@Param("stage") DealStage stage);

    @Query(
            """
            SELECT COALESCE(SUM(COALESCE(d.amount, 0)), 0)
            FROM Deal d
            WHERE d.owner.id = :ownerId AND d.stage = :stage
            """)
    BigDecimal sumAmountByOwnerAndStage(@Param("ownerId") UUID ownerId, @Param("stage") DealStage stage);

    @Query(
            """
            SELECT COALESCE(SUM(COALESCE(d.amount, 0) * d.probability), 0)
            FROM Deal d
            WHERE d.stage NOT IN :stages
            """)
    BigDecimal sumAmountTimesProbabilityWhereStageNotIn(@Param("stages") Collection<DealStage> stages);

    @Query(
            """
            SELECT COALESCE(SUM(COALESCE(d.amount, 0) * d.probability), 0)
            FROM Deal d
            WHERE d.owner.id = :ownerId AND d.stage NOT IN :stages
            """)
    BigDecimal sumAmountTimesProbabilityWhereOwnerAndStageNotIn(
            @Param("ownerId") UUID ownerId, @Param("stages") Collection<DealStage> stages);

    List<Deal> findTop8ByStageNotInOrderByAmountDesc(Collection<DealStage> stages);

    List<Deal> findTop8ByOwner_IdAndStageNotInOrderByAmountDesc(UUID ownerId, Collection<DealStage> stages);

    List<Deal> findTop8ByAccount_IdOrderByAmountDesc(UUID accountId);

    List<Deal> findTop8ByPrimaryContact_IdOrderByUpdatedAtDesc(UUID contactId);
}
