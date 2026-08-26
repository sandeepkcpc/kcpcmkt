package com.kcpc.mkt.reporting.service;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.repository.ContentPlanTalentEntryRepository;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
import com.kcpc.mkt.reporting.dto.AssignableUserOption;
import com.kcpc.mkt.reporting.dto.UserActiveTaskCount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Decorates an assignee-selection candidate list (Shoot/Edit/Publisher/Model pickers - see
 * {@code OperationalEligibilityService}/{@code DeliverableMvcController}) with each candidate's
 * current active-task count, for display alongside their name. Eligibility itself is untouched -
 * this only adds a display attribute to an already-computed candidate list, one grouped COUNT
 * query per stage (never a per-candidate lookup, so the candidate list can be any size without
 * introducing N+1 queries). "Active" is exactly {@link AssigneeActiveWindows}, the same definition
 * {@link TeamWorkloadService}'s Assignee Load panel already uses - never a second one.
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

    @Transactional(readOnly = true)
    public List<AssignableUserOption> withShootCounts(List<User> candidates) {
        return withCounts(candidates,
                shootingAssignmentRepository.countActiveGroupedByCameraperson(AssigneeActiveWindows.SHOOT));
    }

    @Transactional(readOnly = true)
    public List<AssignableUserOption> withEditCounts(List<User> candidates) {
        return withCounts(candidates,
                editingAssignmentRepository.countActiveGroupedByEditor(AssigneeActiveWindows.EDIT));
    }

    @Transactional(readOnly = true)
    public List<AssignableUserOption> withPublishingCounts(List<User> candidates) {
        return withCounts(candidates,
                publishingAssignmentRepository.countActiveGroupedByPublisher(AssigneeActiveWindows.PUBLISHING));
    }

    @Transactional(readOnly = true)
    public List<AssignableUserOption> withModelCounts(List<User> candidates) {
        return withCounts(candidates,
                talentEntryRepository.countActiveGroupedByTalentUser(AssigneeActiveWindows.CLOSED_OUT));
    }

    private List<AssignableUserOption> withCounts(List<User> candidates, List<UserActiveTaskCount> counts) {
        Map<UUID, Long> byUserId = counts.stream()
                .collect(Collectors.toMap(UserActiveTaskCount::getUserId, UserActiveTaskCount::getActiveCount));
        return candidates.stream()
                .map(u -> new AssignableUserOption(u.getId(), u.getFullName(), byUserId.getOrDefault(u.getId(), 0L)))
                .collect(Collectors.toList());
    }
}
