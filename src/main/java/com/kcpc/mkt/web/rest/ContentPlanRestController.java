package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.dto.AssignCameramanRequest;
import com.kcpc.mkt.planning.dto.ContentPlanParametersRequest;
import com.kcpc.mkt.planning.dto.ContentPlanResponse;
import com.kcpc.mkt.planning.dto.PlannedOutputRequest;
import com.kcpc.mkt.planning.dto.PublicationScopeRequest;
import com.kcpc.mkt.planning.dto.SetShootLeadRequest;
import com.kcpc.mkt.planning.dto.StandardScheduleRequest;
import com.kcpc.mkt.planning.dto.TalentSelection;
import com.kcpc.mkt.planning.dto.UrgentScheduleRequest;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.service.PlanningService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** API-OP-020..029 class: Planning stage. */
@RestController
@RequestMapping("/api/v1/content-plans")
public class ContentPlanRestController {

    private final PlanningService planningService;
    private final ContentPlanRepository contentPlanRepository;
    private final UserRepository userRepository;

    public ContentPlanRestController(PlanningService planningService, ContentPlanRepository contentPlanRepository,
                                      UserRepository userRepository) {
        this.planningService = planningService;
        this.contentPlanRepository = contentPlanRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContentPlanResponse> get(@PathVariable UUID id) {
        ContentPlan plan = contentPlanRepository.findById(id).orElseThrow(() -> DomainException.notFound("Content Plan not found"));
        return ResponseEntity.ok(ContentPlanResponse.from(plan));
    }

    @PostMapping("/{id}/parameters")
    public ResponseEntity<ContentPlanResponse> parameters(@PathVariable UUID id,
                                                            @RequestBody ContentPlanParametersRequest request,
                                                            @AuthenticationPrincipal KcpcUserPrincipal principal) {
        // ENG-067: the frozen wire contract (API-OP-017/018) stays plain talent-name strings; the
        // service's internal TalentSelection representation just carries a null user link for them.
        java.util.List<TalentSelection> talentSelections = request.talentNames() == null ? null
                : request.talentNames().stream().map(name -> new TalentSelection(name, null)).toList();
        ContentPlan plan = planningService.updateParameters(principal.user(), id, request.categoryText(),
                request.contentPriority(), request.skuReference(), request.skuNotApplicable(), talentSelections,
                request.folderLink());
        return ResponseEntity.ok(ContentPlanResponse.from(plan));
    }

    @PostMapping("/{id}/schedule/standard")
    public ResponseEntity<ContentPlanResponse> scheduleStandard(@PathVariable UUID id,
                                                                  @Valid @RequestBody StandardScheduleRequest request,
                                                                  @AuthenticationPrincipal KcpcUserPrincipal principal) {
        ContentPlan plan = planningService.setStandardSchedule(principal.user(), id, request.plannedLiveDate(),
                request.shootDateOverride(), request.editDateOverride());
        return ResponseEntity.ok(ContentPlanResponse.from(plan));
    }

    @PostMapping("/{id}/schedule/urgent")
    public ResponseEntity<ContentPlanResponse> scheduleUrgent(@PathVariable UUID id,
                                                                @Valid @RequestBody UrgentScheduleRequest request,
                                                                @AuthenticationPrincipal KcpcUserPrincipal principal) {
        ContentPlan plan = planningService.setUrgentSchedule(principal.user(), id, request.plannedLiveDate(),
                request.shootDate(), request.editDate(), request.urgencyReason());
        return ResponseEntity.ok(ContentPlanResponse.from(plan));
    }

    @PostMapping("/{id}/outputs")
    public ResponseEntity<Void> addOutput(@PathVariable UUID id, @Valid @RequestBody PlannedOutputRequest request,
                                           @AuthenticationPrincipal KcpcUserPrincipal principal) {
        planningService.addPlannedOutput(principal.user(), id, request.outputType(), request.reelType(),
                request.titleDescription());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/outputs/{outputId}/publication-scope")
    public ResponseEntity<Void> publicationScope(@PathVariable UUID outputId,
                                                  @RequestBody PublicationScopeRequest request,
                                                  @AuthenticationPrincipal KcpcUserPrincipal principal) {
        planningService.mapPublicationScope(principal.user(), outputId, request.publicationTargetIds());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/shooting-assignments")
    public ResponseEntity<Void> assignCameraperson(@PathVariable UUID id,
                                                    @Valid @RequestBody AssignCameramanRequest request,
                                                    @AuthenticationPrincipal KcpcUserPrincipal principal) {
        User cameraperson = userRepository.findById(request.cameramanUserId())
                .orElseThrow(() -> DomainException.notFound("User not found: " + request.cameramanUserId()));
        planningService.assignCameraperson(principal.user(), id, cameraperson);
        return ResponseEntity.ok().build();
    }

    /** Not in the frozen API spec - see docs/IMPLEMENTATION_DECISIONS.md ENG-035 (Model(s)-style Shoot Assignment chip-picker). */
    @PostMapping("/{id}/shooting-assignments/{camerapersonUserId}/remove")
    public ResponseEntity<Void> removeCameraperson(@PathVariable UUID id, @PathVariable UUID camerapersonUserId,
                                                    @AuthenticationPrincipal KcpcUserPrincipal principal) {
        planningService.removeCameraperson(principal.user(), id, camerapersonUserId);
        return ResponseEntity.ok().build();
    }

    /** Not in the frozen API spec - see docs/IMPLEMENTATION_DECISIONS.md ENG-036 (Shoot Lead). */
    @PostMapping("/{id}/shooting-assignments/lead")
    public ResponseEntity<Void> setShootLead(@PathVariable UUID id,
                                              @RequestBody(required = false) SetShootLeadRequest request,
                                              @AuthenticationPrincipal KcpcUserPrincipal principal) {
        planningService.setShootLead(principal.user(), id, request == null ? null : request.cameramanUserId());
        return ResponseEntity.ok().build();
    }

    // NOTE (workflow redesign): /planning-review/submit and /planning-review/decision are removed -
    // Planning Review (PLRV/PLAP) is no longer a separate active-workflow gate; a Content Plan is
    // now created already fully planned inside IdeaService#approve (see PlanningApprovalRequest).
}
