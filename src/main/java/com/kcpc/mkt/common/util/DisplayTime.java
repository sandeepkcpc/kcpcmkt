package com.kcpc.mkt.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * SAD-ADR-003: timestamps are stored as UTC ({@code TIMESTAMPTZ}) but displayed to users
 * localized to IST (Asia/Kolkata) - never raw UTC. JSP views call {@link #ist(Instant)} via the
 * {@code kcpc} EL function taglib rather than rendering an {@code Instant} field directly.
 */
public final class DisplayTime {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'IST'");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a");

    private DisplayTime() {
    }

    public static String ist(Instant instant) {
        return instant == null ? "" : FORMAT.format(instant.atZone(IST));
    }

    /** ENG-061: My Ideas' Submitted On column shows date/time on two lines - date part only. */
    public static String istDate(Instant instant) {
        return instant == null ? "" : DATE_FORMAT.format(instant.atZone(IST));
    }

    /** ENG-061: My Ideas' Submitted On column shows date/time on two lines - 12-hour time part only. */
    public static String istTime(Instant instant) {
        return instant == null ? "" : TIME_FORMAT.format(instant.atZone(IST));
    }
}
