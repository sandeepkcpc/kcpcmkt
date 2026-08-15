package com.kcpc.mkt.performance.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ERD-TBL-028: append-only Performance Metric correction history under Permission #9
 * (SAD-DES-025). Mirrors the allowable metrics and N/A states of
 * {@link CreativePerformanceScorecard}; every field is nullable since a correction may touch any
 * subset of metrics. The original sealed scorecard row is never mutated (ERD-CON-060).
 */
@Entity
@Table(name = "performance_metric_corrections")
@AttributeOverride(name = "id", column = @Column(name = "correction_id"))
public class PerformanceMetricCorrection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scorecard_id", nullable = false)
    private CreativePerformanceScorecard scorecard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_correction_id")
    private PerformanceMetricCorrection supersedesCorrection;

    @Column(name = "prior_views_3sec")
    private Integer priorViews3sec;
    @Column(name = "new_views_3sec")
    private Integer newViews3sec;

    @Column(name = "prior_plays")
    private Integer priorPlays;
    @Column(name = "new_plays")
    private Integer newPlays;

    @Column(name = "prior_watch_time", precision = 8, scale = 2)
    private BigDecimal priorWatchTime;
    @Column(name = "new_watch_time", precision = 8, scale = 2)
    private BigDecimal newWatchTime;

    @Column(name = "prior_video_length", precision = 8, scale = 2)
    private BigDecimal priorVideoLength;
    @Column(name = "new_video_length", precision = 8, scale = 2)
    private BigDecimal newVideoLength;

    @Column(name = "prior_clicks")
    private Integer priorClicks;
    @Column(name = "new_clicks")
    private Integer newClicks;

    @Column(name = "prior_impressions")
    private Integer priorImpressions;
    @Column(name = "new_impressions")
    private Integer newImpressions;

    @Column(name = "prior_views_3sec_is_na")
    private Boolean priorViews3secIsNa;
    @Column(name = "new_views_3sec_is_na")
    private Boolean newViews3secIsNa;

    @Column(name = "prior_watch_time_is_na")
    private Boolean priorWatchTimeIsNa;
    @Column(name = "new_watch_time_is_na")
    private Boolean newWatchTimeIsNa;

    @Column(name = "prior_video_length_is_na")
    private Boolean priorVideoLengthIsNa;
    @Column(name = "new_video_length_is_na")
    private Boolean newVideoLengthIsNa;

    @Column(name = "prior_clicks_is_na")
    private Boolean priorClicksIsNa;
    @Column(name = "new_clicks_is_na")
    private Boolean newClicksIsNa;

    @Column(name = "mandatory_reason", nullable = false, columnDefinition = "text")
    private String mandatoryReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corrected_by_user_id", nullable = false)
    private User correctedBy;

    @CreationTimestamp
    @Column(name = "corrected_at", nullable = false, updatable = false)
    private Instant correctedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_permission_grant_id")
    private PermissionGrant actingPermissionGrant;

    protected PerformanceMetricCorrection() {
    }

    public PerformanceMetricCorrection(CreativePerformanceScorecard scorecard,
                                        PerformanceMetricCorrection supersedesCorrection, String mandatoryReason,
                                        User correctedBy, PermissionGrant actingPermissionGrant) {
        this.scorecard = scorecard;
        this.supersedesCorrection = supersedesCorrection;
        this.mandatoryReason = mandatoryReason;
        this.correctedBy = correctedBy;
        this.actingPermissionGrant = actingPermissionGrant;
    }

    public void setViews3sec(Integer prior, Integer next) {
        this.priorViews3sec = prior;
        this.newViews3sec = next;
    }

    public void setPlays(Integer prior, Integer next) {
        this.priorPlays = prior;
        this.newPlays = next;
    }

    public void setWatchTime(BigDecimal prior, BigDecimal next) {
        this.priorWatchTime = prior;
        this.newWatchTime = next;
    }

    public void setVideoLength(BigDecimal prior, BigDecimal next) {
        this.priorVideoLength = prior;
        this.newVideoLength = next;
    }

    public void setClicks(Integer prior, Integer next) {
        this.priorClicks = prior;
        this.newClicks = next;
    }

    public void setImpressions(Integer prior, Integer next) {
        this.priorImpressions = prior;
        this.newImpressions = next;
    }

    public void setViews3secIsNa(Boolean prior, Boolean next) {
        this.priorViews3secIsNa = prior;
        this.newViews3secIsNa = next;
    }

    public void setWatchTimeIsNa(Boolean prior, Boolean next) {
        this.priorWatchTimeIsNa = prior;
        this.newWatchTimeIsNa = next;
    }

    public void setVideoLengthIsNa(Boolean prior, Boolean next) {
        this.priorVideoLengthIsNa = prior;
        this.newVideoLengthIsNa = next;
    }

    public void setClicksIsNa(Boolean prior, Boolean next) {
        this.priorClicksIsNa = prior;
        this.newClicksIsNa = next;
    }

    public CreativePerformanceScorecard getScorecard() {
        return scorecard;
    }

    public PerformanceMetricCorrection getSupersedesCorrection() {
        return supersedesCorrection;
    }

    public Integer getPriorViews3sec() {
        return priorViews3sec;
    }

    public Integer getNewViews3sec() {
        return newViews3sec;
    }

    public Integer getPriorPlays() {
        return priorPlays;
    }

    public Integer getNewPlays() {
        return newPlays;
    }

    public BigDecimal getPriorWatchTime() {
        return priorWatchTime;
    }

    public BigDecimal getNewWatchTime() {
        return newWatchTime;
    }

    public BigDecimal getPriorVideoLength() {
        return priorVideoLength;
    }

    public BigDecimal getNewVideoLength() {
        return newVideoLength;
    }

    public Integer getPriorClicks() {
        return priorClicks;
    }

    public Integer getNewClicks() {
        return newClicks;
    }

    public Integer getPriorImpressions() {
        return priorImpressions;
    }

    public Integer getNewImpressions() {
        return newImpressions;
    }

    public Boolean getPriorViews3secIsNa() {
        return priorViews3secIsNa;
    }

    public Boolean getNewViews3secIsNa() {
        return newViews3secIsNa;
    }

    public Boolean getPriorWatchTimeIsNa() {
        return priorWatchTimeIsNa;
    }

    public Boolean getNewWatchTimeIsNa() {
        return newWatchTimeIsNa;
    }

    public Boolean getPriorVideoLengthIsNa() {
        return priorVideoLengthIsNa;
    }

    public Boolean getNewVideoLengthIsNa() {
        return newVideoLengthIsNa;
    }

    public Boolean getPriorClicksIsNa() {
        return priorClicksIsNa;
    }

    public Boolean getNewClicksIsNa() {
        return newClicksIsNa;
    }

    public String getMandatoryReason() {
        return mandatoryReason;
    }

    public User getCorrectedBy() {
        return correctedBy;
    }

    public Instant getCorrectedAt() {
        return correctedAt;
    }
}
