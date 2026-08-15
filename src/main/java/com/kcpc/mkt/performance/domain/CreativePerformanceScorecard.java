package com.kcpc.mkt.performance.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * ERD-TBL-024: draft-then-sealed scorecard (SC-REQ-002). Mutable while {@code submittedAt} is
 * null; immutable once submitted (ERD-CON-060, also enforced by DB trigger). Zero/null
 * denominators yield N/A, never 0 or an error (SC-REQ-001).
 */
@Entity
@Table(name = "creative_performance_scorecards")
@AttributeOverride(name = "id", column = @Column(name = "scorecard_id"))
public class CreativePerformanceScorecard extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "obligation_id", nullable = false, unique = true)
    private PerformanceObligation obligation;

    @Column(name = "views_3sec")
    private Integer views3sec;

    @Column(name = "plays")
    private Integer plays;

    @Column(name = "average_watch_time_seconds", precision = 8, scale = 2)
    private BigDecimal averageWatchTimeSeconds;

    @Column(name = "video_length_seconds", precision = 8, scale = 2)
    private BigDecimal videoLengthSeconds;

    @Column(name = "link_clicks")
    private Integer linkClicks;

    @Column(name = "impressions")
    private Integer impressions;

    @Column(name = "views_3sec_is_na", nullable = false)
    private boolean views3secIsNa = false;

    @Column(name = "watch_time_is_na", nullable = false)
    private boolean watchTimeIsNa = false;

    @Column(name = "video_length_is_na", nullable = false)
    private boolean videoLengthIsNa = false;

    @Column(name = "clicks_is_na", nullable = false)
    private boolean clicksIsNa = false;

    @Column(name = "hook_rate_percent", precision = 5, scale = 2)
    private BigDecimal hookRatePercent;

    @Column(name = "hold_rate_percent", precision = 5, scale = 2)
    private BigDecimal holdRatePercent;

    @Column(name = "ctr_percent", precision = 5, scale = 2)
    private BigDecimal ctrPercent;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private User recordedBy;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected CreativePerformanceScorecard() {
    }

    public CreativePerformanceScorecard(PerformanceObligation obligation, User recordedBy) {
        this.obligation = obligation;
        this.recordedBy = recordedBy;
    }

    /** SC-REQ-002: draft lifecycle, mutable repeatedly, partial values permitted while unsubmitted. */
    public void updateDraft(Integer views3sec, boolean views3secIsNa, Integer plays,
                             BigDecimal averageWatchTimeSeconds, boolean watchTimeIsNa,
                             BigDecimal videoLengthSeconds, boolean videoLengthIsNa,
                             Integer linkClicks, boolean clicksIsNa, Integer impressions) {
        requireNotSealed();
        this.views3sec = views3sec;
        this.views3secIsNa = views3secIsNa;
        this.plays = plays;
        this.averageWatchTimeSeconds = averageWatchTimeSeconds;
        this.watchTimeIsNa = watchTimeIsNa;
        this.videoLengthSeconds = videoLengthSeconds;
        this.videoLengthIsNa = videoLengthIsNa;
        this.linkClicks = linkClicks;
        this.clicksIsNa = clicksIsNa;
        this.impressions = impressions;
        recomputeRates();
    }

    private void recomputeRates() {
        BigDecimal viewsNumerator = views3secIsNa ? null : toDecimal(views3sec);
        this.hookRatePercent = computeRatePercent(viewsNumerator, toDecimal(plays));

        BigDecimal watchNumerator = watchTimeIsNa ? null : averageWatchTimeSeconds;
        BigDecimal lengthDenominator = videoLengthIsNa ? null : videoLengthSeconds;
        this.holdRatePercent = computeRatePercent(watchNumerator, lengthDenominator);

        BigDecimal clicksNumerator = clicksIsNa ? null : toDecimal(linkClicks);
        this.ctrPercent = computeRatePercent(clicksNumerator, toDecimal(impressions));
    }

    private static BigDecimal toDecimal(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    /** SC-REQ-001: null/N-A numerator or zero/null denominator -&gt; N/A (never 0, never divide-by-zero). */
    private static BigDecimal computeRatePercent(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** ERD-CON-060: seals the record; further changes must go through linked correction records (deferred - see decisions log). */
    public void submit() {
        requireNotSealed();
        this.submittedAt = Instant.now();
    }

    private void requireNotSealed() {
        if (this.submittedAt != null) {
            throw DomainException.conflict(ErrorCode.CONFLICT_STALE_UPDATE,
                    "This scorecard has already been submitted and is sealed (ERD-CON-060)");
        }
    }

    public boolean isSubmitted() {
        return submittedAt != null;
    }

    public PerformanceObligation getObligation() {
        return obligation;
    }

    public Integer getViews3sec() {
        return views3sec;
    }

    public boolean isViews3secIsNa() {
        return views3secIsNa;
    }

    public Integer getPlays() {
        return plays;
    }

    public BigDecimal getAverageWatchTimeSeconds() {
        return averageWatchTimeSeconds;
    }

    public boolean isWatchTimeIsNa() {
        return watchTimeIsNa;
    }

    public BigDecimal getVideoLengthSeconds() {
        return videoLengthSeconds;
    }

    public boolean isVideoLengthIsNa() {
        return videoLengthIsNa;
    }

    public Integer getLinkClicks() {
        return linkClicks;
    }

    public boolean isClicksIsNa() {
        return clicksIsNa;
    }

    public Integer getImpressions() {
        return impressions;
    }

    public BigDecimal getHookRatePercent() {
        return hookRatePercent;
    }

    public BigDecimal getHoldRatePercent() {
        return holdRatePercent;
    }

    public BigDecimal getCtrPercent() {
        return ctrPercent;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
