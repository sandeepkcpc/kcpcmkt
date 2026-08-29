package com.kcpc.mkt.reporting.dto;

/**
 * ENG-075 (defect fix): one real (Planned Output, Publication Target) publishing task's status
 * within a {@link PipelinePlatformSummary}, shown in the Content Pipeline dashboard's per-platform
 * popover. One row per mapping - never deduplicated by Channel, since the same channel can be the
 * target of more than one Planned Output (e.g. several Reel outputs all publishing to the same
 * Facebook page). "Published" means a real {@code ActualPublicationEvent} (ORIGINAL) exists for
 * this EXACT (Planned Output, Publication Target) pair with a non-blank effective Evidence URL -
 * the CURRENT corrected URL if a {@code PublicationEvidenceCorrection} exists, the event's own URL
 * otherwise. A merely-planned mapping with no event is never treated as published.
 * {@code typeLabel} distinguishes rows sharing the same channel (e.g. "REEL · SHORT" vs
 * "REEL · VERY_SHORT") - the popover deliberately has no Output-title column (Type + Channel are
 * enough to tell rows apart there). Plain class, not a record: rendered directly by a JSP popover,
 * whose EL only recognizes getX()/isX() JavaBean accessors (ENG-031).
 */
public class PipelineChannelStatus {

    private final String channelHandle;
    private final String typeLabel;
    private final boolean published;
    private final String evidenceUrl;

    public PipelineChannelStatus(String channelHandle, String typeLabel, boolean published, String evidenceUrl) {
        this.channelHandle = channelHandle;
        this.typeLabel = typeLabel;
        this.published = published;
        this.evidenceUrl = evidenceUrl;
    }

    public String getChannelHandle() {
        return channelHandle;
    }

    /** e.g. "STORY", "POST", "LONG_VIDEO", "REEL" - never null. */
    public String getTypeLabel() {
        return typeLabel;
    }

    public boolean isPublished() {
        return published;
    }

    /** The current effective Evidence URL (already correction-resolved) - null when not published. */
    public String getEvidenceUrl() {
        return evidenceUrl;
    }
}
