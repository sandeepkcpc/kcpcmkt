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
    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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

    /** {@code yyyy-MM-dd} (IST date part) - the exact format an HTML {@code <input type="date">}
     * needs for its {@code value} attribute to prefill correctly; {@link #istDate} ("24 Aug 2026")
     * is for read-only display only and is not a valid date-input value. */
    public static String isoDate(Instant instant) {
        return instant == null ? "" : ISO_DATE_FORMAT.format(instant.atZone(IST));
    }
}
