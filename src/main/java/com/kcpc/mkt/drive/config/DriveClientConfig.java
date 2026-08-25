package com.kcpc.mkt.drive.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.kcpc.mkt.drive.client.DisabledDriveFolderClient;
import com.kcpc.mkt.drive.client.DriveFolderClient;
import com.kcpc.mkt.drive.client.GoogleDriveFolderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Wires the single {@link DriveFolderClient} bean the rest of the app depends on. Real credentials
 * are read from {@code app.drive.service-account-key} (raw JSON, environment-sourced) only when
 * {@code app.drive.enabled=true} - with it false (the default in every environment this app runs
 * in today, since no real credentials exist), the app starts up exactly as before, wired to
 * {@link DisabledDriveFolderClient} instead, and DriveProvisioningService never even reaches this
 * bean because it checks the flag first.
 *
 * <p>Never throws out of this {@code @Bean} method - a Drive misconfiguration (enabled=true but
 * credentials missing/invalid) falls back to {@link DisabledDriveFolderClient} with a loud ERROR
 * log instead of crashing the whole application's startup over one optional integration; the same
 * "loud, never silent" failure surface just shows up on the first real Drive call instead of at
 * boot. This also means a test-provided {@code @Primary} fake {@link DriveFolderClient} bean
 * (see DriveProvisioningServiceTest) always reliably wins injection - @Primary resolution is not
 * order-dependent the way @ConditionalOnMissingBean evaluation against a plain (non-autoconfig)
 * @Configuration class turned out to be.
 */
@Configuration
@EnableConfigurationProperties(DriveProperties.class)
public class DriveClientConfig {

    private static final Logger log = LoggerFactory.getLogger(DriveClientConfig.class);

    @Bean
    public DriveFolderClient driveFolderClient(DriveProperties properties) {
        if (!properties.isEnabled()) {
            return new DisabledDriveFolderClient();
        }
        if (properties.getServiceAccountKey() == null || properties.getServiceAccountKey().isBlank()) {
            log.error("app.drive.enabled is true but app.drive.service-account-key is not configured - "
                    + "Drive provisioning will be inert until this is fixed");
            return new DisabledDriveFolderClient();
        }
        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(properties.getServiceAccountKey().getBytes(StandardCharsets.UTF_8)))
                    .createScoped(List.of(DriveScopes.DRIVE));
            if (properties.getImpersonateUser() != null && !properties.getImpersonateUser().isBlank()) {
                credentials = credentials.createDelegated(properties.getImpersonateUser());
            }
            Drive drive = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("KCPC Bandhani")
                    .build();
            return new GoogleDriveFolderClient(drive, properties.getSharedDriveId());
        } catch (Exception e) {
            log.error("Failed to initialize the Google Drive client from app.drive.service-account-key - "
                    + "Drive provisioning will be inert until this is fixed", e);
            return new DisabledDriveFolderClient();
        }
    }
}
