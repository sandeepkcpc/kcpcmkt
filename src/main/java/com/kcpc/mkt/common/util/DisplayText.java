package com.kcpc.mkt.common.util;

/**
 * Reviews/My Ideas missing-value display rule: render "-" for anything unavailable, never an
 * em-dash. Some shared row DTOs built for other screens (e.g. {@code PipelineRow}, built by
 * {@code PipelineDashboardService} for the unchanged Content Pipeline dashboard) already bake an
 * em-dash placeholder into the field itself at the service layer, so a plain JSP {@code empty}
 * check on that field never trips (the value is never blank - it already IS the em-dash string).
 * This normalizes that literal em-dash to "-" purely at display time, without touching the shared
 * DTO/service Content Pipeline itself still renders unchanged.
 */
public final class DisplayText {

    private static final String EM_DASH = "—";

    private DisplayText() {
    }

    public static String dash(String value) {
        return (value == null || value.isBlank() || EM_DASH.equals(value)) ? "-" : value;
    }
}
