package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.identity.dto.MyTaskResponse;
import com.kcpc.mkt.identity.dto.PersonalMarkResponse;
import com.kcpc.mkt.marks.repository.PersonalMarkAttributionRepository;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Stream;

/**
 * BRS-REQ-066..070 / build-prompt §30: Employee self-service - own tasks and own Marks only.
 * Strict peer privacy: every query here is scoped to the authenticated principal; there is no
 * parameterized "view another user's marks/tasks" path anywhere in this controller.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MySelfServiceRestController {

    private final PersonalMarkAttributionRepository markAttributionRepository;
    private final ShootingAssignmentRepository shootingAssignmentRepository;
    private final EditingAssignmentRepository editingAssignmentRepository;

    public MySelfServiceRestController(PersonalMarkAttributionRepository markAttributionRepository,
                                        ShootingAssignmentRepository shootingAssignmentRepository,
                                        EditingAssignmentRepository editingAssignmentRepository) {
        this.markAttributionRepository = markAttributionRepository;
        this.shootingAssignmentRepository = shootingAssignmentRepository;
        this.editingAssignmentRepository = editingAssignmentRepository;
    }

    @GetMapping("/marks")
    public List<PersonalMarkResponse> myMarks(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return markAttributionRepository.findByRecipient(principal.user()).stream()
                .map(PersonalMarkResponse::from).toList();
    }

    @GetMapping("/tasks")
    public List<MyTaskResponse> myTasks(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        var shootingTasks = shootingAssignmentRepository.findByCamerapersonAndActiveTrue(principal.user()).stream()
                .map(a -> new MyTaskResponse(a.getContentPlan().getId(), a.getContentPlan().getContentId(),
                        "CAMERAPERSON", a.getContentPlan().getWorkflowInstance().getCurrentStatusCode(),
                        a.getContentPlan().getPlannedShootDate(), a.getContentPlan().getPlannedEditDate(),
                        a.getContentPlan().getPlannedLiveDate()));
        var editingTasks = editingAssignmentRepository.findByEditorAndActiveTrue(principal.user()).stream()
                .map(a -> new MyTaskResponse(a.getContentPlan().getId(), a.getContentPlan().getContentId(),
                        "EDITOR", a.getContentPlan().getWorkflowInstance().getCurrentStatusCode(),
                        a.getContentPlan().getPlannedShootDate(), a.getContentPlan().getPlannedEditDate(),
                        a.getContentPlan().getPlannedLiveDate()));
        return Stream.concat(shootingTasks, editingTasks).toList();
    }
}
