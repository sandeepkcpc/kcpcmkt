package com.kcpc.mkt.drive.event;

import java.util.UUID;

/** Published inside the idea-approval transaction, consumed only after that transaction commits
 * (see DriveProvisioningService) - Drive folder provisioning is a slow, network-dependent external
 * call and must never run inside (or be able to roll back) the Content ID creation transaction. */
public record ContentPlanCreatedEvent(UUID contentPlanId) {
}
