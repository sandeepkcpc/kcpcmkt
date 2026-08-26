package com.kcpc.mkt.performance.service;

import com.kcpc.mkt.masterdata.domain.PublicationTarget;
import com.kcpc.mkt.publishing.domain.ActualPublicationEvent;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/**
 * The single governed rule for "does this publication record require Performance tracking" -
 * Instagram/Facebook only (Platform, not Channel - {@code Instagram · kcpc_sikar} and
 * {@code Instagram · kcpc_official} are both eligible; {@code YouTube}, {@code LinkedIn},
 * {@code X}, {@code Pinterest}, and any other platform are never eligible, regardless of channel).
 * Publishing itself remains fully multi-platform - only Performance MEASUREMENT is Meta-only.
 *
 * <p>Reused everywhere the concept applies (per the approved audit): obligation creation
 * ({@code PublishingService}), the Performance tab query and completion logic
 * ({@code PerformanceService}), and the KPI Dashboard's pending/overdue/completion queries
 * ({@code KpiDashboardService}, via {@link #ELIGIBLE_PLATFORM_NAMES_SQL_LIST}) - never a duplicated
 * {@code "Instagram" || "Facebook"} check at each call site.
 *
 * <p>Matched by {@link com.kcpc.mkt.masterdata.domain.Platform#getPlatformName()}, case-insensitive
 * and trimmed - the platform catalogue has no separate immutable "type" field to key off (it is a
 * plain, CEO-renamable {@code platform_name} string, see {@code Platform.rename}), so this is the
 * most canonical identifier actually available in the current schema. If the "Instagram" or
 * "Facebook" catalogue row is ever renamed via Administration -&gt; Catalogue, this constant must be
 * updated too - the same class of risk that already exists for every other catalogue-name match in
 * this codebase (e.g. Business Role name resolution).
 */
@Service
public class PerformanceEligibilityService {

    private static final Set<String> ELIGIBLE_PLATFORM_NAMES = Set.of("instagram", "facebook");

    /** Same governed name set as {@link #ELIGIBLE_PLATFORM_NAMES}, as a native-SQL {@code IN} list
     * literal - used by {@code KpiDashboardService}'s native queries, which cannot call this Java
     * method directly. Kept next to the Java set (not in a separate file) specifically so the two
     * can never silently drift apart; {@code PerformanceEligibilityReconciliationTest} asserts they
     * agree. */
    public static final String ELIGIBLE_PLATFORM_NAMES_SQL_LIST = "'instagram','facebook'";

    public boolean isEligible(ActualPublicationEvent event) {
        return event != null && isEligible(event.getPublicationTarget());
    }

    public boolean isEligible(PublicationTarget target) {
        if (target == null || target.getPlatform() == null) {
            return false;
        }
        String platformName = target.getPlatform().getPlatformName();
        return platformName != null
                && ELIGIBLE_PLATFORM_NAMES.contains(platformName.trim().toLowerCase(Locale.ROOT));
    }
}
