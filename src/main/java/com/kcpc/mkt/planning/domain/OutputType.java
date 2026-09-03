package com.kcpc.mkt.planning.domain;

/** BRS-REQ-023 / ERD-CON-007. Redesigned (V31): PHOTOGRAPHY/VIDEO replaced by STORY/POST/
 *  LONG_VIDEO - REEL unchanged. Existing PHOTOGRAPHY rows remapped to POST, VIDEO to LONG_VIDEO
 *  (see V31__output_type_redesign.sql). "Short Clip" was renamed to VIDEO (now LONG_VIDEO) in R3.4.
 *  Persisted by name ({@code @Enumerated(EnumType.STRING)}, see PlannedOutput), so this declared
 *  order is purely presentational - every "Planned Outputs" UI (Idea Review Planning form, Content
 *  Detail's "+ Add Output") iterates {@code values()} directly rather than each keeping its own
 *  hardcoded row order, so reordering here is the single source of truth for all of them at once. */
public enum OutputType {
    REEL,
    STORY,
    POST,
    LONG_VIDEO
}
