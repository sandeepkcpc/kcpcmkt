package com.kcpc.mkt.web.mvc.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * "/app/my-shoots" (Model employee) row - one Content Plan this Model is a linked
 * {@code ContentPlanTalentEntry} on (ENG-067). Plain class, not a record: rendered directly by a
 * JSP, whose EL only recognizes getX() JavaBean accessors, not a record's canonical accessors
 * (ENG-031).
 */
public class MyShootRow {

    private final UUID contentPlanId;
    private final String contentId;
    private final String title;
    private final LocalDate plannedShootDate;
    private final String myRole;
    private final String otherTalent;
    private final String statusLabel;

    public MyShootRow(UUID contentPlanId, String contentId, String title, LocalDate plannedShootDate, String myRole,
                       String otherTalent, String statusLabel) {
        this.contentPlanId = contentPlanId;
        this.contentId = contentId;
        this.title = title;
        this.plannedShootDate = plannedShootDate;
        this.myRole = myRole;
        this.otherTalent = otherTalent;
        this.statusLabel = statusLabel;
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

    public String getStatusLabel() {
        return statusLabel;
    }
}
