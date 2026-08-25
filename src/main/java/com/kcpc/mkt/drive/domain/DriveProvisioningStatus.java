package com.kcpc.mkt.drive.domain;

/**
 * NOT_STARTED -&gt; IN_PROGRESS -&gt; SUCCEEDED | FAILED. Retry is only permitted from
 * NOT_STARTED/FAILED; a row in IN_PROGRESS is being provisioned right now (by this request or a
 * concurrent one) and must not be picked up again until it resolves.
 */
public enum DriveProvisioningStatus {
    NOT_STARTED,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED
}
