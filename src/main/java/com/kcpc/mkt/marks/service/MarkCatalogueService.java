package com.kcpc.mkt.marks.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.marks.domain.MarkCatalogueEntry;
import com.kcpc.mkt.marks.domain.RoleType;
import com.kcpc.mkt.marks.repository.MarkCatalogueEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ENG-092: admin-configurable catalogue of allowed Cameraperson/Editor/Model Mark values, replacing
 * the previously-hardcoded {@code [0, 0.5, 1.0, 2.0, 3.0]} list. CEO_OWNER only, no delegation -
 * unlike Publishing Catalogue (Permission #17), mark values feed performance-mark attribution and
 * warrant the same native-CEO-only gate as Users/Business Roles/Permissions. Deletion here is a
 * real, permanent delete (not soft-deactivate like Platforms/Channels) - safe because
 * predefined_role_marks/predefined_mark_corrections store the mark value directly, never a
 * foreign key to this table, so removing a catalogue entry never touches historical data.
 */
@Service
public class MarkCatalogueService {

    private final MarkCatalogueEntryRepository repository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public MarkCatalogueService(MarkCatalogueEntryRepository repository, AuthorizationService authorizationService,
                                 AuditService auditService) {
        this.repository = repository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    private void requireCeo(User actor) {
        authorizationService.requireAccessClass(actor, AccessClass.CEO_OWNER, "Mark Catalogue management");
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A catalogue reason is mandatory");
        }
    }

    private void requireValue(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Mark value must be a non-negative number");
        }
    }

    @Transactional
    public MarkCatalogueEntry createEntry(User actor, RoleType roleType, BigDecimal value, String reason) {
        requireCeo(actor);
        requireReason(reason);
        if (roleType == null) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Applicable Role is mandatory");
        }
        requireValue(value);
        if (repository.findByRoleTypeAndMarkValue(roleType, value).isPresent()) {
            throw DomainException.conflict(ErrorCode.CONFLICT_DUPLICATE_SUBMISSION,
                    "A mark catalogue entry for this role and value already exists");
        }
        MarkCatalogueEntry entry = repository.save(new MarkCatalogueEntry(roleType, value));
        auditService.record(actor, Optional.empty(), "MARK_CATALOGUE", "MARK_CREATED", "mark_catalogue_entries",
                entry.getId(), reason);
        return entry;
    }

    @Transactional
    public MarkCatalogueEntry updateEntry(User actor, UUID id, BigDecimal newValue, Boolean active, String reason) {
        requireCeo(actor);
        requireReason(reason);
        MarkCatalogueEntry entry = repository.findById(id)
                .orElseThrow(() -> DomainException.notFound("Mark Catalogue entry not found: " + id));
        if (newValue != null && newValue.compareTo(entry.getMarkValue()) != 0) {
            requireValue(newValue);
            repository.findByRoleTypeAndMarkValue(entry.getRoleType(), newValue).ifPresent(existing -> {
                throw DomainException.conflict(ErrorCode.CONFLICT_DUPLICATE_SUBMISSION,
                        "A mark catalogue entry for this role and value already exists");
            });
            entry.changeValue(newValue);
        }
        if (active != null) {
            if (active) {
                entry.activate();
            } else {
                entry.deactivate();
            }
        }
        repository.save(entry);
        auditService.record(actor, Optional.empty(), "MARK_CATALOGUE", "MARK_UPDATED", "mark_catalogue_entries",
                entry.getId(), reason);
        return entry;
    }

    @Transactional
    public void deleteEntry(User actor, UUID id, String reason) {
        requireCeo(actor);
        requireReason(reason);
        MarkCatalogueEntry entry = repository.findById(id)
                .orElseThrow(() -> DomainException.notFound("Mark Catalogue entry not found: " + id));
        // Audited before deletion - the row (and its id) won't exist to reference afterward, so the
        // reason/role/value are the only record left that this entry ever existed.
        auditService.record(actor, Optional.empty(), "MARK_CATALOGUE", "MARK_DELETED", "mark_catalogue_entries",
                entry.getId(), entry.getRoleType() + " " + entry.getMarkValue() + " - " + reason);
        repository.delete(entry);
    }

    /** Used by IdeaService before constructing/correcting a PredefinedRoleMarks - the real
     * "is this an allowed value for this role right now" check (BigDecimal#compareTo, not equals,
     * so scale differences like "0" vs "0.0" still match). */
    public void requireActiveValue(RoleType roleType, BigDecimal value) {
        if (value == null || repository.findByRoleTypeAndMarkValue(roleType, value)
                .filter(MarkCatalogueEntry::isActive).isEmpty()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    roleType + " Mark must be one of the currently active Mark Catalogue values");
        }
    }

    public List<MarkCatalogueEntry> listAll() {
        return repository.findAllByOrderByRoleTypeAscMarkValueAsc();
    }

    public List<MarkCatalogueEntry> listActiveByRole(RoleType roleType) {
        return repository.findByRoleTypeAndActiveTrueOrderByMarkValueAsc(roleType);
    }
}
