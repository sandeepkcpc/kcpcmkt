package com.kcpc.mkt.common.repository;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Narrow repository base for append-only entities (ERD-CON-035/047/058 etc.): exposes only
 * save (insert) and read operations - no update/delete methods are reachable through this
 * interface. See docs/IMPLEMENTATION_DECISIONS.md "DB-001" for the full (DB-privilege-layer)
 * enforcement plan deferred to Phase 14.
 */
@NoRepositoryBean
public interface InsertOnlyRepository<T, ID> extends Repository<T, ID> {

    T save(T entity);

    Optional<T> findById(ID id);

    List<T> findAll();
}
