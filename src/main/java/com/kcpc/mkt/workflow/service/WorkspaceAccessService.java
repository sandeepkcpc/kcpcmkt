package com.kcpc.mkt.workflow.service;

import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.planning.repository.ContentPlanTalentEntryRepository;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
import org.springframework.stereotype.Service;

/**
 * Module-aware operational-workspace reachability for a non-production-by-default EMPLOYEE
 * (participates_in_workflow = false): each {@code /app/**} module has its own explicit permission/
 * assignment requirement, never a single "holds any permission -&gt; unlocks everything" rule.
 * Consumed by {@link com.kcpc.mkt.web.mvc.WorkflowParticipationInterceptor} for route reachability
 * and by {@link com.kcpc.mkt.web.mvc.MvcNavigationAdvice} for nav link visibility - both call these
 * same methods, so nav and route enforcement can never disagree. This is a reachability convenience
 * only: the underlying controllers/services still independently re-check the real authorization for
 * the specific action/stage/item, exactly as before (hiding a module here never substitutes for
 * backend authorization on the action itself).
 */
@Service
public class WorkspaceAccessService {

    private final AuthorizationService authorizationService;
    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final PublishingAssignmentRepository publishingAssignmentRepository;
    private final ContentPlanTalentEntryRepository talentEntryRepository;

    public WorkspaceAccessService(AuthorizationService authorizationService,
                                   ShootingAssignmentRepository shootingAssignmentRepository,
                                   EditingAssignmentRepository editingAssignmentRepository,
                                   PublishingAssignmentRepository publishingAssignmentRepository,
                                   ContentPlanTalentEntryRepository talentEntryRepository) {
        this.authorizationService = authorizationService;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.publishingAssignmentRepository = publishingAssignmentRepository;
        this.talentEntryRepository = talentEntryRepository;
    }

    /**
     * My Work / deliverable-detail execution reachability: an explicit PERM_18/19/08 grant (any),
     * OR an active Shoot/Edit/Publishing assignment (so a user whose permission was later revoked
     * can still reach the page and see the "execution blocked" state - spec section 13/16.2). ALSO
     * covers Assignment Management (PERM_04/PERM_06/PERM_11) - the delegated Shoot/Edit assignment
     * queue lives under My Work too, so a PERM_04-only employee (assignment authority, never
     * execution) needs the same module reachability as an execution-permission holder.
     */
    public boolean canReachMyWork(User user) {
        if (authorizationService.hasAnyActiveGrant(user, OperationalPermission.PERM_18_SHOOT_EXECUTION,
                OperationalPermission.PERM_19_EDIT_EXECUTION, OperationalPermission.PERM_08_PUBLISHING_EXECUTION,
                OperationalPermission.PERM_04_SHOOT_ASSIGNMENT, OperationalPermission.PERM_06_EDIT_ASSIGNMENT,
                OperationalPermission.PERM_11_REASSIGN,
                // PERM_13's admin actions (Drive folder retry/relink) live on the deliverable's own
                // Content Detail page - a holder needs the same /app/deliverables reachability
                // PERM_11 (also an administrative-only permission) already gets.
                OperationalPermission.PERM_13_FOLDER_LINK_MANAGE)) {
            return true;
        }
        return !shootingAssignmentRepository.findByCamerapersonAndActiveTrue(user).isEmpty()
                || !editingAssignmentRepository.findByEditorAndActiveTrue(user).isEmpty()
                || !publishingAssignmentRepository.findByPublisherAndActiveTrue(user).isEmpty();
    }

    /** Reviews reachability: any of the three review permissions Reviews still has a tab for. */
    public boolean canReachReviews(User user) {
        return authorizationService.hasAnyActiveGrant(user, OperationalPermission.PERM_01_IDEA_REVIEW,
                OperationalPermission.PERM_05_SHOOT_REVIEW, OperationalPermission.PERM_07_EDIT_REVIEW);
    }

    /** Team Workload reachability: PERM_14 only. */
    public boolean canReachTeamWorkload(User user) {
        return authorizationService.hasAnyActiveGrant(user, OperationalPermission.PERM_14_TEAM_WORKLOAD_VIEW);
    }

    /** KPI Reports reachability: PERM_15 only. */
    public boolean canReachKpiReports(User user) {
        return authorizationService.hasAnyActiveGrant(user, OperationalPermission.PERM_15_TEAM_KPI_VIEW);
    }

    /** Audit/Logs reachability: PERM_16 only. */
    public boolean canReachLogs(User user) {
        return authorizationService.hasAnyActiveGrant(user, OperationalPermission.PERM_16_AUDIT_HISTORY_VIEW);
    }

    /** Administration -> Publishing Catalogue reachability: PERM_17 only. */
    public boolean canReachCatalogue(User user) {
        return authorizationService.hasAnyActiveGrant(user, OperationalPermission.PERM_17_PLATFORM_CATALOGUE_MANAGE);
    }

    /**
     * My Performance reachability: the same grant/assignment test as {@link #canReachMyWork}, PLUS
     * any Model/Talent participation ({@code ContentPlanTalentEntry}) - a Model earns marks and
     * completed-task history purely through talent linkage, never a Shoot/Edit/Publishing
     * assignment record of their own, so {@code canReachMyWork} alone would under-cover them.
     */
    public boolean canReachMyPerformance(User user) {
        if (canReachMyWork(user)) {
            return true;
        }
        return !talentEntryRepository.findByTalentUser(user).isEmpty();
    }

    /** Any module at all - used only to decide whether the interceptor should evaluate module routing. */
    public boolean canReachAnyModule(User user) {
        return canReachMyWork(user) || canReachReviews(user) || canReachTeamWorkload(user)
                || canReachKpiReports(user) || canReachLogs(user) || canReachCatalogue(user);
    }
}
