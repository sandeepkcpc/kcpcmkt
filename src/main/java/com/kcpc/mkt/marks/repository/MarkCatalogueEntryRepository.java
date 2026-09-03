package com.kcpc.mkt.marks.repository;

import com.kcpc.mkt.marks.domain.MarkCatalogueEntry;
import com.kcpc.mkt.marks.domain.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarkCatalogueEntryRepository extends JpaRepository<MarkCatalogueEntry, UUID> {
    List<MarkCatalogueEntry> findAllByOrderByRoleTypeAscMarkValueAsc();

    List<MarkCatalogueEntry> findByRoleTypeAndActiveTrueOrderByMarkValueAsc(RoleType roleType);

    Optional<MarkCatalogueEntry> findByRoleTypeAndMarkValue(RoleType roleType, BigDecimal markValue);
}
