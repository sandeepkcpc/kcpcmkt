package com.kcpc.mkt.planning.domain;

/** BRS-REQ-023 / ERD-CON-007. Redesigned (V31): PHOTOGRAPHY/VIDEO replaced by STORY/POST/
 *  LONG_VIDEO - REEL unchanged. Existing PHOTOGRAPHY rows remapped to POST, VIDEO to LONG_VIDEO
 *  (see V31__output_type_redesign.sql). "Short Clip" was renamed to VIDEO (now LONG_VIDEO) in R3.4. */
public enum OutputType {
    STORY,
    POST,
    REEL,
    LONG_VIDEO
}
