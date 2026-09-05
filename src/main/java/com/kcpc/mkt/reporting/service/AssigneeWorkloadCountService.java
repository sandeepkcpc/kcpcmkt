package com.kcpc.mkt.reporting.service;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.repository.ContentPlanTalentEntryRepository;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
import com.kcpc.mkt.reporting.dto.AssignableUserOption;
import com.kcpc.mkt.reporting.dto.UserContentPlanRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decorates an assignee-selection candidate list (Model/Cameraperson/Editor/Publisher pickers - see
 * {@code OperationalEligibilityService}/{@code DeliverableMvcController}/{@code ReviewsMvcController})
 * with each candidate's current active-task count, for display alongside their name. Eligibility
 * itself is untouched - this only adds a display attribute to an already-computed candidate list.
 *
 * <p><strong>Counting unit: one DISTINCT Content Plan per employee</strong>, matching
 * {@link TeamWorkloadService}'s Assignee Load exactly. One real-world unit of work is "this employee
 * currently has actionable work on this Content ID", so an employee holding several roles on the
 * SAME Content ID (Model + Cameraperson on one shoot, say) is 1 active task, not 2. Different
 * Content IDs still count separately, and two different employees on one Content ID each get 1.
 *
 * <p>That means the count is deliberately <em>cross-role</em>: every picker shows the same number
 * for the same employee, because the number answers "how much work does this person currently have",
 * not "how much work of this one kind". Which of an employee's rows are eligible to contribute is
 * still decided per role by {@link AssigneeActiveWindows} - a Cameraperson's Shoot assignment stops
 * counting once the plan reaches Edit, and a Model's talent entry is gated by the SHOOT window
 * specifically (a Model's work is tied to the Shoot stage), exactly as
 * {@code TeamWorkloadService#modelRow} already gates it.
 *
 * <p>Three defects this replaced, all of which made a picker disagree with Team Workload about the
 * same employee: the queries counted ROWS rather than distinct Content Plans (so two rows for one
 * employee on one plan read as 2); each picker counted only its own stage in isolation; and the
 * Model picker used the broad "not yet closed out" set instead of the SHOOT window, so a Model kept
 * showing tasks long after the shoot was done.
 *
 * <p>Four grouped queries in total, never a per-candidate lookup, so the candidate list can be any
 * size without introducing N+1 queries.
 */
@Service
public class AssigneeWorkloadCountService {

    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;
    private final PublishingAssignmentRepository publishingAssignmentRepository;
    private final ContentPlanTalentEntryRepository talentEntryRepository;

    public AssigneeWorkloadCountService(ShootingAssignmentRepository shootingAssignmentRepository,
                                         EditingAssignmentRepository editingAssignmentRepository,
                                         PublishingAssignmentRepository publishingAssignmentRepository,
                                         ContentPlanTalentEntryRepository talentEntryRepository) {
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
        this.publishingAssignmentRepository = publishingAssignmentRepository;
        this.talentEntryRepository = talentEntryRepository;
    }

    // The four entry points are kept separate so every existing caller is unchanged. They now all
    // resolve to the same employee-level number, by design - see the class javadoc.

    @Transactional(readOnly = true)
    public List<AssignableUserOption> withShootCounts(List<User> candidates) {
        return withCounts(candidates);
    }

    @Transactional(readOnly = true)
    public List<AssignableUserOption> withEditCounts(List<User> candidates) {
        return withCounts(candidates);
    }

    @Transactional(readOnly = true)
    public List<AssignableUserOption> withPublishingCounts(List<User> candidates) {
        return withCounts(candidates);
    }

    @Transactional(readOnly = true)
    public List<AssignableUserOption> withModelCounts(List<User> candidates) {
        return withCounts(candidates);
    }

    /**
     * Every Content Plan each user is currently active on, unioned across all four roles. A
     * {@link Set} per user is what performs the de-duplication: the same Content Plan arriving again
     * from a second role - or from a second row of the same role - collapses into the entry it
     * already has, so {@code size()} is the distinct-Content-Plan count.
     */
    private Map<UUID, Set<UUID>> activeContentPlansByUser() {
        Map<UUID, Set<UUID>> byUser = new HashMap<>();
        collect(byUser, shootingAssignmentRepository
                .findActiveContentPlanRefsByCameraperson(AssigneeActiveWindows.SHOOT));
        collect(byUser, editingAssignmentRepository
                .findActiveContentPlanRefsByEditor(AssigneeActiveWindows.EDIT));
        collect(byUser, publishingAssignmentRepository
                .findActiveContentPlanRefsByPublisher(AssigneeActiveWindows.PUBLISHING));
        // Model/Talent rides the SHOOT window, not CLOSED_OUT - see the class javadoc.
        collect(byUser, talentEntryRepository
                .findActiveContentPlanRefsByTalentUser(AssigneeActiveWindows.SHOOT));
        return byUser;
    }

    private static void collect(Map<UUID, Set<UUID>> byUser, List<UserContentPlanRef> refs) {
        for (UserContentPlanRef ref : refs) {
            if (ref.getUserId() == null || ref.getContentPlanId() == null) {
                continue;
            }
            byUser.computeIfAbsent(ref.getUserId(), id -> new LinkedHashSet<>()).add(ref.getContentPlanId());
        }
    }

    private List<AssignableUserOption> withCounts(List<User> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of(); // nothing to decorate - skip the queries entirely
        }
        Map<UUID, Set<UUID>> byUser = activeContentPlansByUser();
        return candidates.stream()
                .map(u -> new AssignableUserOption(u.getId(), u.getFullName(),
                        (long) byUser.getOrDefault(u.getId(), Set.of()).size()))
                .toList();
    }
}
