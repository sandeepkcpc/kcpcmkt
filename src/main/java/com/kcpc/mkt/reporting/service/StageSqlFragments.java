package com.kcpc.mkt.reporting.service;

/**
 * Shared native-SQL CASE fragments for "which of the 5 governed stages (Planning/Shoot/Edit/
 * Publishing/Performance) is this Content Plan currently in, and what is its current approved
 * target date for that stage" - BR-039's "Current Approved Planned Date is stage-context, not a
 * fixed single column." Extracted here (previously duplicated verbatim in both {@link KpiService}
 * and {@link AdminReportingService}) so the KPI Dashboard, the 30-KPI console, and Delayed
 * Deliverables can never disagree about stage boundaries - matches the already-shipped Content
 * Detail stepper (deliverable-detail.jsp's cdStageIndex 0..4).
 */
final class StageSqlFragments {

    private StageSqlFragments() {
    }

    static final String STAGE_PLANNED_DATE_CASE =
            "CASE WHEN wi.current_status_code IN ('PL','PLRV','PLAP','SA','SIP','SRV') THEN cp.planned_shoot_date "
                    + "WHEN wi.current_status_code IN ('SAP','EA','ED','ERV') THEN cp.planned_edit_date "
                    + "WHEN wi.current_status_code IN ('EAP','RFP','PUBG','PP','PFUP') THEN cp.planned_live_date "
                    + "ELSE NULL END";

    static final String STAGE_LABEL_CASE =
            "CASE WHEN wi.current_status_code IN ('PL','PLRV') THEN 'Planning' "
                    + "WHEN wi.current_status_code IN ('PLAP','SA','SIP','SRV','SAP') THEN 'Shoot' "
                    + "WHEN wi.current_status_code IN ('EA','ED','ERV') THEN 'Edit' "
                    + "WHEN wi.current_status_code IN ('EAP','RFP','PUBG') THEN 'Publishing' "
                    + "WHEN wi.current_status_code IN ('PP','PFUP') THEN 'Performance' "
                    + "ELSE 'Other' END";

    /** Current-stage assignee(s) only - Planning's preparer(s), Shoot/Edit's active assignment(s);
     * Publishing/Performance have no single-owner assignment table, resolves to NULL. */
    static final String ASSIGNED_TO_CASE =
            "CASE WHEN wi.current_status_code IN ('PL','PLRV') THEN "
                    + "(select string_agg(u.full_name, ', ' order by u.full_name) from planning_preparers pp "
                    + "join users u on u.user_id = pp.preparer_user_id where pp.content_plan_id = cp.content_plan_id) "
                    + "WHEN wi.current_status_code IN ('PLAP','SA','SIP','SRV','SAP') THEN "
                    + "(select string_agg(u.full_name, ', ' order by u.full_name) from shooting_assignments sa "
                    + "join users u on u.user_id = sa.cameraperson_user_id "
                    + "where sa.content_plan_id = cp.content_plan_id and sa.is_active = true) "
                    + "WHEN wi.current_status_code IN ('EA','ED','ERV') THEN "
                    + "(select string_agg(u.full_name, ', ' order by u.full_name) from editing_assignments ea "
                    + "join users u on u.user_id = ea.editor_user_id "
                    + "where ea.content_plan_id = cp.content_plan_id and ea.is_active = true) "
                    + "ELSE NULL END";
}
