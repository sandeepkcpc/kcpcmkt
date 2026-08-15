package com.kcpc.mkt.planning.service;

import com.kcpc.mkt.planning.domain.ContentIdSequence;
import com.kcpc.mkt.planning.repository.ContentIdSequenceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * BRS-REQ-020 / ERD-CON-038: allocates {@code C-MMYY-NNNN} with a monthly sequence reset, keyed
 * by IST (SAD-ADR-003) business month, via SELECT ... FOR UPDATE row locking - never MAX()+1.
 * Must run inside the same transaction as the Idea Approval command that allocates it
 * (REQUIRES, the default propagation, is sufficient since callers are always @Transactional).
 */
@Service
public class ContentIdAllocationService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter MMYY_FORMAT = DateTimeFormatter.ofPattern("MMyy");

    private final ContentIdSequenceRepository sequenceRepository;

    public ContentIdAllocationService(ContentIdSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String allocateContentId() {
        String mmyy = ZonedDateTime.now(BUSINESS_ZONE).format(MMYY_FORMAT);
        ContentIdSequence sequence = sequenceRepository.findForUpdate(mmyy).orElseGet(() -> {
            try {
                return sequenceRepository.saveAndFlush(new ContentIdSequence(mmyy, 0));
            } catch (DataIntegrityViolationException raceLoser) {
                // Another concurrent first-of-the-month allocation won the insert race; the row
                // now exists, so re-fetch it under the proper FOR UPDATE lock.
                return sequenceRepository.findForUpdate(mmyy)
                        .orElseThrow(() -> raceLoser);
            }
        });
        int next = sequence.nextSequence();
        if (next > 9999) {
            throw new IllegalStateException("Content ID monthly sequence exhausted for " + mmyy);
        }
        return "C-%s-%04d".formatted(mmyy, next);
    }
}
