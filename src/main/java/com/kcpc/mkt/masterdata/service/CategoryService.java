package com.kcpc.mkt.masterdata.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.masterdata.domain.Category;
import com.kcpc.mkt.masterdata.repository.CategoryRepository;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ENG-094: admin-configurable catalogue of allowed Planning Category values, replacing the
 * previously-unconstrained free-text Category field. CEO_OWNER only, no delegation - matches Mark
 * Catalogue's authorization gate (ENG-092), not Publishing Catalogue's broader PERM_17 delegation.
 *
 * <p>Unlike Mark Catalogue (a real hard delete is always safe there - no FK ever references a mark
 * value), a Category name IS matched by {@code ContentPlan.categoryText} - so delete here is only
 * permitted when no Content Plan currently uses this category's name; otherwise the caller must
 * deactivate instead, exactly as this class's own {@link #deleteEntry} enforces.
 */
@Service
public class CategoryService {

    private final CategoryRepository repository;
    private final ContentPlanRepository contentPlanRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public CategoryService(CategoryRepository repository, ContentPlanRepository contentPlanRepository,
                            AuthorizationService authorizationService, AuditService auditService) {
        this.repository = repository;
        this.contentPlanRepository = contentPlanRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    private void requireCeo(User actor) {
        authorizationService.requireAccessClass(actor, AccessClass.CEO_OWNER, "Category Catalogue management");
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A catalogue reason is mandatory");
        }
    }

    private void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Category Name is mandatory");
        }
    }

    @Transactional
    public Category createEntry(User actor, String name, String reason) {
        requireCeo(actor);
        requireReason(reason);
        requireName(name);
        String trimmed = name.trim();
        if (repository.findByNameIgnoreCase(trimmed).isPresent()) {
            throw DomainException.conflict(ErrorCode.CONFLICT_DUPLICATE_SUBMISSION,
                    "A category with this name already exists");
        }
        Category entry = repository.save(new Category(trimmed, false));
        auditService.record(actor, Optional.empty(), "CATEGORY_CATALOGUE", "CATEGORY_CREATED", "categories",
                entry.getId(), reason);
        return entry;
    }

    @Transactional
    public Category updateEntry(User actor, UUID id, String newName, Boolean active, String reason) {
        requireCeo(actor);
        requireReason(reason);
        Category entry = repository.findById(id)
                .orElseThrow(() -> DomainException.notFound("Category Catalogue entry not found: " + id));

        if (newName != null && !newName.isBlank() && !newName.trim().equalsIgnoreCase(entry.getName())) {
            if (entry.isDefaultCategory()) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "The default N/A category cannot be renamed");
            }
            String trimmed = newName.trim();
            repository.findByNameIgnoreCase(trimmed).ifPresent(existing -> {
                throw DomainException.conflict(ErrorCode.CONFLICT_DUPLICATE_SUBMISSION,
                        "A category with this name already exists");
            });
            entry.rename(trimmed);
        }
        if (active != null && active != entry.isActive()) {
            if (!active && entry.isDefaultCategory()) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "The default N/A category cannot be deactivated");
            }
            if (active) {
                entry.activate();
            } else {
                entry.deactivate();
            }
        }
        repository.save(entry);
        auditService.record(actor, Optional.empty(), "CATEGORY_CATALOGUE", "CATEGORY_UPDATED", "categories",
                entry.getId(), reason);
        return entry;
    }

    @Transactional
    public void deleteEntry(User actor, UUID id, String reason) {
        requireCeo(actor);
        requireReason(reason);
        Category entry = repository.findById(id)
                .orElseThrow(() -> DomainException.notFound("Category Catalogue entry not found: " + id));
        if (entry.isDefaultCategory()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "The default N/A category cannot be deleted");
        }
        if (contentPlanRepository.existsByCategoryTextIgnoreCase(entry.getName())) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "This category is already used by existing Content Plans - deactivate it instead of deleting "
                            + "it, so those records keep their historical category");
        }
        auditService.record(actor, Optional.empty(), "CATEGORY_CATALOGUE", "CATEGORY_DELETED", "categories",
                entry.getId(), entry.getName() + " - " + reason);
        repository.delete(entry);
    }

    /** Used by IdeaService/PlanningService before recording a Content Plan's category - the real
     * "is this an allowed category right now" check. A blank/null value is always allowed (Category
     * has always been optional; the dropdown itself defaults to N/A, but any caller - including
     * every pre-existing API/test caller - that never sends this field must keep working exactly
     * as before). */
    public void requireActiveNameOrBlank(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        boolean valid = repository.findByNameIgnoreCase(name.trim())
                .filter(Category::isActive)
                .isPresent();
        if (!valid) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Category must be one of the currently active Category Catalogue values");
        }
    }

    private List<Category> defaultFirstThenAlphabetical(List<Category> categories) {
        return categories.stream()
                .sorted(Comparator.comparing(Category::isDefaultCategory).reversed()
                        .thenComparing(c -> c.getName().toLowerCase()))
                .toList();
    }

    public List<Category> listAll() {
        return defaultFirstThenAlphabetical(repository.findAllByOrderByNameAsc());
    }

    /** N/A always first, per Planning Category dropdown requirements. */
    public List<Category> listActive() {
        return defaultFirstThenAlphabetical(repository.findByActiveTrueOrderByNameAsc());
    }
}
