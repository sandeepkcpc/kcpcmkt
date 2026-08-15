package com.kcpc.mkt.planning.repository;

import com.kcpc.mkt.planning.domain.ContentIdSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ContentIdSequenceRepository extends JpaRepository<ContentIdSequence, String> {

    /** ERD-CON-038: atomic locking per IST month via SELECT ... FOR UPDATE - MAX(content_id)+1 is prohibited. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ContentIdSequence s where s.businessMonthMmyy = :mmyy")
    Optional<ContentIdSequence> findForUpdate(String mmyy);
}
