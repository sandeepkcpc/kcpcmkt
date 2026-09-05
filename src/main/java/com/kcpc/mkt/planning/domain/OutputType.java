package com.kcpc.mkt.planning.domain;

import java.util.Arrays;
import java.util.List;

/** BRS-REQ-023 / ERD-CON-007. Redesigned (V31): PHOTOGRAPHY/VIDEO replaced by STORY/POST/
 *  LONG_VIDEO - REEL unchanged. Existing PHOTOGRAPHY rows remapped to POST, VIDEO to LONG_VIDEO
 *  (see V31__output_type_redesign.sql). "Short Clip" was renamed to VIDEO (now LONG_VIDEO) in R3.4.
 *  Persisted by name ({@code @Enumerated(EnumType.STRING)}, see PlannedOutput), so this declared
 *  order is purely presentational - every "Planned Outputs" UI (Idea Review Planning form, Content
 *  Detail's "+ Add Output") iterates {@link #selectableValues()} directly rather than each keeping
 *  its own hardcoded row order, so reordering here is the single source of truth for all of them
 *  at once.
 *
 *  <p><strong>Retired constants.</strong> A constant marked non-selectable is closed to NEW usage
 *  but remains a fully valid, readable value: it is deliberately NOT removed from this enum and
 *  NOT migrated in the database. Removing it would break {@code @Enumerated(EnumType.STRING)}
 *  deserialization of every historical {@code planned_outputs} row that still carries it - those
 *  rows must keep rendering normally in Content Detail, Pipeline, KPI and history views. The
 *  retirement is enforced at the two edges instead: it is absent from {@link #selectableValues()}
 *  (so no creation UI offers it) and rejected by the services on create (so no API can introduce
 *  it). The database CHECK constraint is intentionally left alone for the same reason - historical
 *  rows must stay valid against it.
 */
public enum OutputType {
    REEL(true),
    /**
     * Retired: closed to new Planned Outputs. Existing STORY rows are untouched and keep
     * displaying everywhere - see {@code KpiService}, which still counts STORY alongside POST for
     * historical static/image content.
     */
    STORY(false),
    POST(true),
    LONG_VIDEO(true);

    private final boolean selectable;

    OutputType(boolean selectable) {
        this.selectable = selectable;
    }

    /** Whether this type may be chosen for a NEW Planned Output. False for retired constants. */
    public boolean isSelectable() {
        return selectable;
    }

    /**
     * The Output Types offerable in any creation/edit UI, in declared order. Every "Planned
     * Outputs" screen renders from this rather than {@code values()}, so retiring a constant
     * removes it from all of them at once and can never be missed on one screen.
     */
    public static List<OutputType> selectableValues() {
        return Arrays.stream(values()).filter(OutputType::isSelectable).toList();
    }
}
