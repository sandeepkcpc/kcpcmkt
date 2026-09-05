package com.kcpc.mkt.reporting.dto;

import java.util.UUID;

/**
 * Spring Data JPA interface projection for "which Content Plan is this user currently active on" -
 * one row per (user, Content Plan, source row). Deliberately NOT pre-aggregated in SQL: an employee
 * can hold several roles on the SAME Content Plan (Model + Cameraperson on one shoot, say), and
 * those rows live in different tables, so the de-duplication has to happen once across the union of
 * all of them rather than per query. See {@code AssigneeWorkloadCountService}.
 */
public interface UserContentPlanRef {

    UUID getUserId();

    UUID getContentPlanId();
}
