package com.kcpc.mkt.planning.repository;

import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.domain.PlannedOutputPublicationTargetMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PlannedOutputPublicationTargetMappingRepository extends JpaRepository<PlannedOutputPublicationTargetMapping, UUID> {
    List<PlannedOutputPublicationTargetMapping> findByPlannedOutput(PlannedOutput plannedOutput);

    List<PlannedOutputPublicationTargetMapping> findByPlannedOutput_IdIn(Collection<UUID> plannedOutputIds);
}
