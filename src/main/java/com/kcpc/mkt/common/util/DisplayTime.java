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

    private DisplayTime() {
    }

    public static String ist(Instant instant) {
        return instant == null ? "" : FORMAT.format(instant.atZone(IST));
    }
}
