package com.kcpc.mkt.drive.repository;

import com.kcpc.mkt.drive.domain.ContentDriveProvisioning;
import com.kcpc.mkt.planning.domain.ContentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface ContentDriveProvisioningRepository extends JpaRepository<ContentDriveProvisioning, UUID> {
    Optional<ContentDriveProvisioning> findByContentPlan(ContentPlan contentPlan);

    Optional<ContentDriveProvisioning> findByContentPlan_Id(UUID contentPlanId);

    /** Row-locked read for the provisioning critical section (mark IN_PROGRESS -&gt; call Drive -&gt;
     * mark SUCCEEDED/FAILED) - prevents two concurrent requests (the post-commit event listener and
     * an admin-triggered retry, or two retries) from both passing the canRetry() check and racing
     * to create the same folders twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ContentDriveProvisioning p where p.contentPlan.id = :contentPlanId")
    Optional<ContentDriveProvisioning> findByContentPlanIdForUpdate(@Param("contentPlanId") UUID contentPlanId);
}
