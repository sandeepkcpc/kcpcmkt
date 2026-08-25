package com.kcpc.mkt.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit coverage for the shared KPI Dashboard numeric display helpers - no Spring context
 * needed, these are pure functions. Covers the exact examples from the formatting-fix spec: no-data
 * "-", singular/plural "day"/"days", 1-decimal rounding, and thousands-separated whole-number
 * count formatting.
 */
class DisplayNumberTest {

    @Test
    void daysRendersDashForNull() {
        assertThat(DisplayNumber.days(null)).isEqualTo("-");
    }

    @Test
    void daysRendersSingularAtExactlyOne() {
        assertThat(DisplayNumber.days(1)).isEqualTo("1 day");
        assertThat(DisplayNumber.days(1.0)).isEqualTo("1 day");
    }

    @Test
    void daysRendersPluralForZeroAndOtherWholeValues() {
        assertThat(DisplayNumber.days(0)).isEqualTo("0 days");
        assertThat(DisplayNumber.days(0.0)).isEqualTo("0 days");
        assertThat(DisplayNumber.days(2)).isEqualTo("2 days");
    }

    @Test
    void daysRoundsFractionalValuesToOneDecimalAndStaysPlural() {
        assertThat(DisplayNumber.days(3.4)).isEqualTo("3.4 days");
        assertThat(DisplayNumber.days(1.5)).isEqualTo("1.5 days");
        // Rounds to 1 decimal, matching the precision already used everywhere else in the app.
        assertThat(DisplayNumber.days(3.44)).isEqualTo("3.4 days");
        assertThat(DisplayNumber.days(3.46)).isEqualTo("3.5 days");
    }

    @Test
    void daysDropsUnnecessaryTrailingZeroWhenRoundingLandsOnAWholeNumber() {
        // 0.95 rounds to 1.0 at 1-decimal precision - must render as the whole "1 day", never "1.0 day".
        assertThat(DisplayNumber.days(0.95)).isEqualTo("1 day");
    }

    @Test
    void countRendersDashForNull() {
        assertThat(DisplayNumber.count(null)).isEqualTo("-");
    }

    @Test
    void countFormatsWithThousandsSeparatorAndNoUnnecessaryDecimals() {
        assertThat(DisplayNumber.count(4448.15)).isEqualTo("4,448");
        assertThat(DisplayNumber.count(5000.00)).isEqualTo("5,000");
        assertThat(DisplayNumber.count(1234567)).isEqualTo("1,234,567");
    }
}
