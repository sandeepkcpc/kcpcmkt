package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.identity.dto.ReasonRequest;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import com.kcpc.mkt.workflow.dto.CancelRequest;
import com.kcpc.mkt.workflow.dto.ReassignRequest;
import com.kcpc.mkt.workflow.dto.ReopenCompletedRequest;
import com.kcpc.mkt.workflow.dto.RescheduleRequest;
import com.kcpc.mkt.workflow.service.AdminActionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Build-prompt §28 / API-OP admin class: Reschedule, Reassign, Cancel, Reopen Completed. */
@RestController
@RequestMapping("/api/v1/content-plans/{id}")
public class AdminActionRestController {

    private final AdminActionService adminActionService;

    public AdminActionRestController(AdminActionService adminActionService) {
        this.adminActionService = adminActionService;
    }

    @PostMapping("/reschedule")
    public ResponseEntity<Void> reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest request,
                                            @AuthenticationPrincipal KcpcUserPrincipal principal) {
        adminActionService.reschedule(principal.user(), id, request.stageContext(), request.newShootDate(),
                request.newEditDate(), request.newLiveDate(), request.reason());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reassign")
    public ResponseEntity<Void> reassign(@PathVariable UUID id, @Valid @RequestBody ReassignRequest request,
                                          @AuthenticationPrincipal KcpcUserPrincipal principal) {
        adminActionService.reassign(principal.user(), id, request.taskStage(), request.newAssigneeUserIds(),
                request.reason());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest request,
                                        @AuthenticationPrincipal KcpcUserPrincipal principal) {
        adminActionService.cancel(principal.user(), id, request.reason());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reopen")
    public ResponseEntity<Void> reopen(@PathVariable UUID id, @Valid @RequestBody ReopenCompletedRequest request,
                                        @AuthenticationPrincipal KcpcUserPrincipal principal) {
        adminActionService.reopenCompleted(principal.user(), id, request.purpose(), request.reason());
        return ResponseEntity.ok().build();
    }

    /** API-OP-052: Reopen Completed Deliverable for Publishing (Permission #8 Action), COMP -&gt; PUBG. */
    @PostMapping("/reopen-publishing")
    public ResponseEntity<Void> reopenPublishing(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request,
                                                  @AuthenticationPrincipal KcpcUserPrincipal principal) {
        adminActionService.reopenForPublishing(principal.user(), id, request.reason());
        return ResponseEntity.ok().build();
    }

    /** API-OP-053: Reopen Completed Deliverable for Metric Correction (Permission #9 Action), COMP -&gt; PFUP. */
    @PostMapping("/reopen-performance")
    public ResponseEntity<Void> reopenPerformance(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request,
                                                   @AuthenticationPrincipal KcpcUserPrincipal principal) {
        adminActionService.reopenForPerformance(principal.user(), id, request.reason());
        return ResponseEntity.ok().build();
    }
}
