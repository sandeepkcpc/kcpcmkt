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
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableConfigurationProperties(DriveProperties.class)
public class DriveClientConfig {

    private static final Logger log = LoggerFactory.getLogger(DriveClientConfig.class);

    @Bean
    public DriveFolderClient driveFolderClient(DriveProperties properties) {

        if (!properties.isEnabled()) {
            return new DisabledDriveFolderClient();
        }

        if (properties.getServiceAccountKey() == null
                || properties.getServiceAccountKey().isBlank()) {

            log.error(
                    "app.drive.enabled is true but app.drive.service-account-key is not configured"
            );

            return new DisabledDriveFolderClient();
        }

        try {

            String serviceAccountKey = properties.getServiceAccountKey();

            InputStream credentialsStream;

            File credentialFile = new File(serviceAccountKey);

            // Production: file path
            if (credentialFile.exists() && credentialFile.isFile()) {

                log.info("Loading Google Drive credentials from file: {}", serviceAccountKey);

                credentialsStream = new FileInputStream(credentialFile);

            }
            // Development: raw JSON
            else {

                log.info("Loading Google Drive credentials from raw JSON");

                credentialsStream = new ByteArrayInputStream(
                        serviceAccountKey.getBytes(StandardCharsets.UTF_8)
                );
            }


            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(credentialsStream)
                    .createScoped(List.of(DriveScopes.DRIVE));


            if (properties.getImpersonateUser() != null
                    && !properties.getImpersonateUser().isBlank()) {

                credentials = credentials.createDelegated(
                        properties.getImpersonateUser()
                );
            }


            Drive drive = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials)
            )
                    .setApplicationName("KCPC Bandhani")
                    .build();


            return new GoogleDriveFolderClient(
                    drive,
                    properties.getSharedDriveId()
            );


        } catch (Exception e) {

            log.error(
                    "Failed to initialize Google Drive client. Drive provisioning disabled until fixed",
                    e
            );

            return new DisabledDriveFolderClient();
        }
    }
}