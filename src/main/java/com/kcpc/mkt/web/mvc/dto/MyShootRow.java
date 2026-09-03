package com.kcpc.mkt.web.mvc.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * "/app/my-shoots" (Model employee) row - one Content Plan this Model is a linked
 * {@code ContentPlanTalentEntry} on (ENG-067). Plain class, not a record: rendered directly by a
 * JSP, whose EL only recognizes getX() JavaBean accessors, not a record's canonical accessors
 * (ENG-031).
 *
 * <p>Deliberately carries no overall Content/Workflow status: a Model's participation is
 * independent of the downstream content lifecycle (Edit/Review/Publishing) - their task is
 * considered complete once the shoot is assigned/approved per the existing workflow, and is never
 * re-derived as "pending" by a later stage. Exposing the raw {@code WorkflowStatus} here (as this
 * row used to) would leak internal lifecycle detail the Model has no reason to track; the
 * "Shoot Execution screen" reached via View (see DeliverableMvcController#view's Model branch)
 * shows the actually-relevant Shoot-phase-only progress instead.
 */
public class MyShootRow {

    private final UUID contentPlanId;
    private final String contentId;
    private final String title;
    private final LocalDate plannedShootDate;
    private final String myRole;
    private final String otherTalent;

    public MyShootRow(UUID contentPlanId, String contentId, String title, LocalDate plannedShootDate, String myRole,
                       String otherTalent) {
        this.contentPlanId = contentPlanId;
        this.contentId = contentId;
        this.title = title;
        this.plannedShootDate = plannedShootDate;
        this.myRole = myRole;
        this.otherTalent = otherTalent;
    }

    public UUID getContentPlanId() {
        return contentPlanId;
    }

    public String getContentId() {
        return contentId;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getPlannedShootDate() {
        return plannedShootDate;
    }

    public String getMyRole() {
        return myRole;
    }

    public String getOtherTalent() {
        return otherTalent;
    }
}
