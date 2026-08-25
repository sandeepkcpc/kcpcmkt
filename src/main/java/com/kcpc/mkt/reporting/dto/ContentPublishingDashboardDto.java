package com.kcpc.mkt.reporting.dto;

import java.math.BigDecimal;
import java.util.List;

/** KPI Dashboard -&gt; Content &amp; Publishing (spec §21-27). */
public class ContentPublishingDashboardDto {

    private final long publishedContent;
    private final long originalCount;
    private final long repostCount;
    private final long evidenceCorrectionCount;
    private final BigDecimal evidenceCorrectionRatePercent;
    private final List<LabelCountRow> contentMix;
    private final List<LabelCountRow> platformDistribution;
    private final List<LabelCountRow> channelDistribution;
    private final TargetCompletionDto targetCompletion;

    public ContentPublishingDashboardDto(long publishedContent, long originalCount, long repostCount,
                                          long evidenceCorrectionCount, BigDecimal evidenceCorrectionRatePercent,
                                          List<LabelCountRow> contentMix, List<LabelCountRow> platformDistribution,
                                          List<LabelCountRow> channelDistribution, TargetCompletionDto targetCompletion) {
        this.publishedContent = publishedContent;
        this.originalCount = originalCount;
        this.repostCount = repostCount;
        this.evidenceCorrectionCount = evidenceCorrectionCount;
        this.evidenceCorrectionRatePercent = evidenceCorrectionRatePercent;
        this.contentMix = contentMix;
        this.platformDistribution = platformDistribution;
        this.channelDistribution = channelDistribution;
        this.targetCompletion = targetCompletion;
    }

    public long getPublishedContent() {
        return publishedContent;
    }

    public long getOriginalCount() {
        return originalCount;
    }

    public long getRepostCount() {
        return repostCount;
    }

    public long getEvidenceCorrectionCount() {
        return evidenceCorrectionCount;
    }

    public BigDecimal getEvidenceCorrectionRatePercent() {
        return evidenceCorrectionRatePercent;
    }

    public List<LabelCountRow> getContentMix() {
        return contentMix;
    }

    public List<LabelCountRow> getPlatformDistribution() {
        return platformDistribution;
    }

    public List<LabelCountRow> getChannelDistribution() {
        return channelDistribution;
    }

    public TargetCompletionDto getTargetCompletion() {
        return targetCompletion;
    }
}
