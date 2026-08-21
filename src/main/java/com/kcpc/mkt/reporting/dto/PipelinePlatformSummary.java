package com.kcpc.mkt.reporting.dto;

import java.util.List;

/**
 * ENG-075: one Platform's roll-up for the Content Pipeline dashboard's Platforms column - every
 * distinct planned (Platform, Channel) pair for the Content ID, grouped by Platform, each channel
 * carrying its own {@link PipelineChannelStatus}. Plain class, not a record: rendered directly by
 * a JSP popover, whose EL only recognizes getX()/isX() JavaBean accessors (ENG-031).
 */
public class PipelinePlatformSummary {

    private final String platformName;
    private final int totalChannels;
    private final int publishedChannels;
    private final List<PipelineChannelStatus> channels;

    public PipelinePlatformSummary(String platformName, int totalChannels, int publishedChannels,
                                    List<PipelineChannelStatus> channels) {
        this.platformName = platformName;
        this.totalChannels = totalChannels;
        this.publishedChannels = publishedChannels;
        this.channels = channels;
    }

    public String getPlatformName() {
        return platformName;
    }

    public int getTotalChannels() {
        return totalChannels;
    }

    public int getPublishedChannels() {
        return publishedChannels;
    }

    /** Active/clickable icon style whenever at least one of this platform's channels is published. */
    public boolean isAnyPublished() {
        return publishedChannels > 0;
    }

    public boolean isFullyPublished() {
        return totalChannels > 0 && publishedChannels == totalChannels;
    }

    public List<PipelineChannelStatus> getChannels() {
        return channels;
    }
}
