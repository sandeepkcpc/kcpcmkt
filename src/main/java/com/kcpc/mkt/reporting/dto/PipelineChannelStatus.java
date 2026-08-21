package com.kcpc.mkt.reporting.dto;

/**
 * ENG-075: one Channel's publication status within a {@link PipelinePlatformSummary}, shown in the
 * Content Pipeline dashboard's per-platform popover. "Published" means a real
 * {@code ActualPublicationEvent} (ORIGINAL) exists for this exact (Content Plan, Publication
 * Target) with a non-blank effective Evidence URL - the CURRENT corrected URL if a
 * {@code PublicationEvidenceCorrection} exists, the event's own URL otherwise. A merely-planned
 * {@code PlannedOutputPublicationTargetMapping} is never treated as published. Plain class, not a
 * record: rendered directly by a JSP popover, whose EL only recognizes getX()/isX() JavaBean
 * accessors (ENG-031).
 */
public class PipelineChannelStatus {

    private final String channelHandle;
    private final boolean published;
    private final String evidenceUrl;

    public PipelineChannelStatus(String channelHandle, boolean published, String evidenceUrl) {
        this.channelHandle = channelHandle;
        this.published = published;
        this.evidenceUrl = evidenceUrl;
    }

    public String getChannelHandle() {
        return channelHandle;
    }

    public boolean isPublished() {
        return published;
    }

    /** The current effective Evidence URL (already correction-resolved) - null when not published. */
    public String getEvidenceUrl() {
        return evidenceUrl;
    }
}
