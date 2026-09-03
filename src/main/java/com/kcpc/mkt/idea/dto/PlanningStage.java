package com.kcpc.mkt.idea.dto;

/**
 * ENG-091: which pipeline stage a newly-approved Content Plan starts at. Exactly 3 combinations of
 * these are valid on {@link PlanningApprovalRequest#stages()} - {@code {SHOOT,EDIT,PUBLISHING}}
 * (Standard), {@code {EDIT,PUBLISHING}} (Direct Edit), {@code {PUBLISHING}} (Direct Publishing) -
 * a "starting point" selector, not an arbitrary subset (see {@code IdeaService#approve}).
 * PUBLISHING is always present; it can never be excluded.
 */
public enum PlanningStage {
    SHOOT,
    EDIT,
    PUBLISHING
}
