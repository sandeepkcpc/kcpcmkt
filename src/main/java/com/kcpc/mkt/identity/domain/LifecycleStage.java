package com.kcpc.mkt.identity.domain;

/** ERD-TBL-036 permission_grant_stages: the 7 governed lifecycle stages, 1..7. */
public enum LifecycleStage {
    IDEA_MANAGEMENT(1),
    PLANNING(2),
    SHOOTING(3),
    EDITING(4),
    PUBLISHING(5),
    PERFORMANCE(6),
    ADMINISTRATIVE(7);

    private final int number;

    LifecycleStage(int number) {
        this.number = number;
    }

    public int number() {
        return number;
    }

    public static LifecycleStage fromNumber(int number) {
        for (LifecycleStage s : values()) {
            if (s.number == number) {
                return s;
            }
        }
        throw new IllegalArgumentException("No such lifecycle stage number: " + number);
    }
}
