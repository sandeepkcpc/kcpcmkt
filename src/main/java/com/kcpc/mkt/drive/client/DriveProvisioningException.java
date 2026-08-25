package com.kcpc.mkt.drive.client;

/** Wraps any failure talking to the Google Drive API (auth, network, quota, not-found parent,
 * etc.) into one checked type the provisioning service persists as {@code last_error} - never
 * silently swallowed. */
public class DriveProvisioningException extends Exception {

    public DriveProvisioningException(String message) {
        super(message);
    }

    public DriveProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
