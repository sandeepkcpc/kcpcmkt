package com.kcpc.mkt.planning.domain;

import java.util.Arrays;
import java.util.List;

/** BRS-REQ-021 / ERD-CON-055.
 *
 *  <p><strong>Retired constants.</strong> A constant marked non-selectable is closed to NEW usage
 *  but remains a fully valid, readable value: it is deliberately NOT removed from this enum and
 *  NOT migrated in the database. Removing it would break {@code @Enumerated(EnumType.STRING)}
 *  deserialization of every existing {@code content_plans} row that still carries it - those rows
 *  must keep displaying normally (Content Detail renders its own priority pill styling for each
 *  value, including retired ones). The retirement is enforced at the two edges instead: it is
 *  absent from {@link #selectableValues()} (so no Planning form offers it) and rejected by
 *  {@code PlanningService}/{@code IdeaService} when it would be newly introduced (so no API can
 *  set it). The database CHECK constraint is intentionally left alone for the same reason -
 *  existing rows must stay valid against it.
 */
public enum ContentPriority {
    LOW(true),
    /**
     * Retired: closed to new Planning records. Existing MEDIUM plans are untouched and keep
     * displaying with their own priority pill everywhere.
     */
    MEDIUM(false),
    HIGH(true);

    private final boolean selectable;

    ContentPriority(boolean selectable) {
        this.selectable = selectable;
    }

    /** Whether this priority may be chosen for a NEW/edited plan. False for retired constants. */
    public boolean isSelectable() {
        return selectable;
    }

    /**
     * The priorities offerable in any Planning form, in declared order. Every Planning Basics
     * screen renders from this rather than {@code values()}, so retiring a constant removes it
     * from all of them at once and can never be missed on one screen.
     */
    public static List<ContentPriority> selectableValues() {
        return Arrays.stream(values()).filter(ContentPriority::isSelectable).toList();
    }
}
