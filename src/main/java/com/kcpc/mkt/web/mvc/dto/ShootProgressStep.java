package com.kcpc.mkt.web.mvc.dto;

import java.time.Instant;

/**
 * One node of an employee Task Detail page's display-only progress tracker (Shoot: ENG-064; Edit:
 * ENG-066) - "done"/"current"/"pending" derived purely from the plan's real {@code WorkflowStatus}
 * and its {@code WorkflowTransitionHistory}/{@code ReviewCycle} data, never a new backend status.
 * Plain class, not a record (ENG-031).
 */
public class ShootProgressStep {

    private final String label;
    private final String state;
    private final Instant date;

    public ShootProgressStep(String label, String state, Instant date) {
        this.label = label;
        this.state = state;
        this.date = date;
    }

    public String getLabel() {
        return label;
    }

    public String getState() {
        return state;
    }

    public Instant getDate() {
        return date;
    }
}
