package com.kcpc.mkt.identity.domain;

/**
 * ERD-TBL-004 operational_permissions catalogue (fixed reference data, seeded by V1 migration).
 * Represented as an enum since the 17-permission catalogue is governed/frozen (API_Specification.md §9).
 */
public enum OperationalPermission {
    PERM_01_IDEA_REVIEW(1),
    PERM_02_PLANNING_EXECUTION(2),
    PERM_03_PLANNING_REVIEW(3),
    PERM_04_SHOOT_ASSIGNMENT(4),
    PERM_05_SHOOT_REVIEW(5),
    PERM_06_EDIT_ASSIGNMENT(6),
    PERM_07_EDIT_REVIEW(7),
    PERM_08_PUBLISHING_EXECUTION(8),
    PERM_09_PERFORMANCE_UPDATE(9),
    PERM_10_RESCHEDULE(10),
    PERM_11_REASSIGN(11),
    PERM_12_CANCEL(12),
    PERM_13_FOLDER_LINK_MANAGE(13),
    PERM_14_TEAM_WORKLOAD_VIEW(14),
    PERM_15_TEAM_KPI_VIEW(15),
    PERM_16_AUDIT_HISTORY_VIEW(16),
    PERM_17_PLATFORM_CATALOGUE_MANAGE(17);

    private final int number;

    OperationalPermission(int number) {
        this.number = number;
    }

    public int number() {
        return number;
    }

    public static OperationalPermission fromNumber(int number) {
        for (OperationalPermission p : values()) {
            if (p.number == number) {
                return p;
            }
        }
        throw new IllegalArgumentException("No such operational permission number: " + number);
    }
}
