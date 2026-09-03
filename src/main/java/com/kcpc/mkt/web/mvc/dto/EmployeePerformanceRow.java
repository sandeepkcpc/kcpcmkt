package com.kcpc.mkt.web.mvc.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * "/app/my-performance" - one completed Content Plan involvement for the logged-in employee,
 * across every mechanism that can earn them a mark or a completed-work record: Cameraperson
 * (ShootingAssignment), Editor (EditingAssignment), Publisher (PublishingAssignment), or Model/
 * Talent (ContentPlanTalentEntry). Only ever built for the AUTHENTICATED user - see
 * LandingMvcController#myPerformance, which derives the employee from the session/JWT principal
 * only, never from a request parameter. Plain class, not a record: rendered directly by a JSP,
 * whose EL only recognizes getX() JavaBean accessors (ENG-031).
 *
 * <p>Mark/markMax are both null for a Publisher row (Publishing has no mark-attribution gate at
 * all in this system - see RoleType, which has no PUBLISHER value) rather than fabricating a mark
 * that was never actually awarded.
 */
public class EmployeePerformanceRow {

    private final UUID contentPlanId;
    private final String contentId;
    private final String title;
    private final String stage; // "SHOOT" / "EDIT" / "PUBLISH" - the real WorkflowStage classification (Model rows use "SHOOT", the stage they actually participated in - there is no separate "Model" stage in the data model)
    private final String roleLabel; // "Cameraperson" / "Editor" / "Publisher" / "Model"
    private final Instant completedOn;
    private final LocalDate plannedDate;
    private final Integer delayDays; // negative = early, 0 = on time, positive = delayed; null if either date is unknown
    private final String delayStatus; // "DELAYED" / "ON_TIME" / "EARLY" / null
    private final BigDecimal mark; // null when this role never receives a mark (Publisher)
    private final BigDecimal markMax; // the Mark Catalogue's current active max for this role, at query time; null alongside mark
    private final String resultLabel; // the deciding review's own outcome (e.g. "Approved") - reused from the same ReviewCycle data My Work's Completed Work already surfaces, never a new qualitative scale
    private final String remarks;

    public EmployeePerformanceRow(UUID contentPlanId, String contentId, String title, String stage, String roleLabel,
                                   Instant completedOn, LocalDate plannedDate, Integer delayDays, String delayStatus,
                                   BigDecimal mark, BigDecimal markMax, String resultLabel, String remarks) {
        this.contentPlanId = contentPlanId;
        this.contentId = contentId;
        this.title = title;
        this.stage = stage;
        this.roleLabel = roleLabel;
        this.completedOn = completedOn;
        this.plannedDate = plannedDate;
        this.delayDays = delayDays;
        this.delayStatus = delayStatus;
        this.mark = mark;
        this.markMax = markMax;
        this.resultLabel = resultLabel;
        this.remarks = remarks;
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

    public String getStage() {
        return stage;
    }

    public String getRoleLabel() {
        return roleLabel;
    }

    public Instant getCompletedOn() {
        return completedOn;
    }

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public Integer getDelayDays() {
        return delayDays;
    }

    public String getDelayStatus() {
        return delayStatus;
    }

    public BigDecimal getMark() {
        return mark;
    }

    public BigDecimal getMarkMax() {
        return markMax;
    }

    public String getResultLabel() {
        return resultLabel;
    }

    public String getRemarks() {
        return remarks;
    }
}
