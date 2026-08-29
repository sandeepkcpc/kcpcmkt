package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.drive.domain.DriveProvisioningStatus;
import com.kcpc.mkt.drive.repository.ContentDriveProvisioningRepository;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for a real production incident: Content Detail showed "Drive folders not
 * yet provisioned" + Retry Provisioning; clicking Retry produced a GREEN "Drive folder
 * provisioning retried." banner (looked like success) while nothing actually happened, because
 * app.drive.enabled was false in that environment - AdminActionService/DriveProvisioningService
 * never reached Google at all. Root cause was environmental (DRIVE_ENABLED not set in the shell
 * that launched the server), not a code bug, but the misleading flash message WAS a real bug -
 * this test locks in the fix.
 *
 * <p>Deliberately does NOT override app.drive.enabled - runs against the real application default
 * (false), the same as every other non-Drive-specific test in this suite and the same state the
 * incident occurred in.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DriveProvisioningDisabledFeedbackTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    ContentDriveProvisioningRepository provisioningRepository;

    @Test
    void retryingWithDriveIntegrationDisabledShowsAnHonestInfoMessageNeverAMisleadingSuccess() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String camId = createUser(ceo, "Disabled Drive Cam", "disabled-drive-cam-" + unique + "@kcpcbandhani.local");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"drive disabled feedback test grant\"}");
        // Workflow redesign: approval carries every former Planning field and transitions straight
        // to Shoot Assigned (SA), never PL/PLRV/PLAP.
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Disabled Drive Feedback " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + java.time.LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/disabled-drive-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"]}}");
        Idea ideaEntity = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(ideaEntity).orElseThrow();

        // The tracking row is always created (cheap, no network call) regardless of the enabled
        // flag - confirms this is the exact "NOT_STARTED, never touched" state the incident showed.
        assertThat(provisioningRepository.findByContentPlan(plan).orElseThrow().getStatus())
                .isEqualTo(DriveProvisioningStatus.NOT_STARTED);

        ceo.postFormAjax("/app/deliverables/" + plan.getId() + "/drive/retry", Map.of());

        assertThat(provisioningRepository.findByContentPlan(plan).orElseThrow().getStatus())
                .as("Retry with Drive disabled must remain a true no-op - never silently transition status")
                .isEqualTo(DriveProvisioningStatus.NOT_STARTED);

        String pageAfterRetry = ceo.get("/app/deliverables/" + plan.getId()).body();
        assertThat(pageAfterRetry)
                .as("Must show the honest, non-misleading disabled-integration message")
                .contains("alert-info")
                .contains("Drive integration is not enabled on this server")
                .doesNotContain("alert-success\">Drive folder provisioning retried.")
                .doesNotContain("alert-success\">Drive folders provisioned successfully.");
    }

    private String createUser(TestApiClient ceo, String fullName, String email) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"01926e3e-0001-7000-8000-000000000004\",\"creationReason\":\"drive disabled feedback test fixture\"}");
        return response.get("userId").asText();
    }
}
