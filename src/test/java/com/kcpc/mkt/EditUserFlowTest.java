package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Users admin page -&gt; Edit User modal (AdminMvcController#updateUser -&gt; UserAdminService#updateUser):
 * Full Name/Email/Business Role/Status saved together, CEO-only, mandatory reason, immutable
 * userId. Drives the real HTTP surface (MVC AJAX POST, real Postgres) - no mocking.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EditUserFlowTest {

    @LocalServerPort
    int port;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String createEmployee(TestApiClient ceo, String fullName, String email, String businessRoleId)
            throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"OriginalPassw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"edit user test fixture\"}");
        return response.get("userId").asText();
    }

    private HttpResponse<String> editUser(TestApiClient actor, String userId, String fullName, String email,
                                           String businessRoleId, boolean active, String reason) throws Exception {
        return actor.postFormAjax("/app/admin/users/" + userId + "/edit", Map.of(
                "fullName", fullName, "email", email, "businessRoleId", businessRoleId,
                "active", String.valueOf(active), "reason", reason));
    }

    @Test
    void ceoEditsNameEmailRoleAndStatusInOneSave() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String userId = createEmployee(ceo, "Edit Flow Original", "edit-flow-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID);

        HttpResponse<String> response = editUser(ceo, userId, "  Edit Flow Renamed  ",
                "edit-flow-renamed-" + unique + "@kcpcbandhani.local", VIDEO_EDITOR_ROLE_ID, false,
                "Correcting onboarding details");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = MAPPER.readTree(response.body());
        // Trimmed, not the raw leading/trailing-whitespace value that was submitted.
        assertThat(body.get("fullName").asText()).isEqualTo("Edit Flow Renamed");
        assertThat(body.get("email").asText()).isEqualTo("edit-flow-renamed-" + unique + "@kcpcbandhani.local");
        assertThat(body.get("businessRoleName").asText()).isEqualTo("Video Editor");
        // Access Class is never independently submitted - it is resolved from the new Business
        // Role automatically (BRS-REQ-001/002).
        assertThat(body.get("accessClass").asText()).isEqualTo("EMPLOYEE");
        assertThat(body.get("active").asBoolean()).isFalse();
        assertThat(body.get("userId").asText()).isEqualTo(userId);

        // Audit trail: USER_UPDATED, actor CEO, target this user, reason preserved.
        JsonNode auditLogs = ceo.getJson("/api/v1/audit/logs?actionType=USER_UPDATED");
        boolean found = false;
        for (JsonNode entry : auditLogs) {
            if (entry.get("targetEntityId").asText().equals(userId)) {
                found = true;
                assertThat(entry.get("actionReason").asText()).isEqualTo("Correcting onboarding details");
                assertThat(entry.get("eventCategory").asText()).isEqualTo("USER_ADMIN");
            }
        }
        assertThat(found).as("USER_UPDATED audit entry for the edited user").isTrue();

        // The account is now deactivated - even with correct (and renamed) credentials, login is
        // rejected (AuthenticationApplicationService: AUTH_ACCOUNT_INACTIVE, 401) - proving Status
        // actually took effect, not just the display cell.
        TestApiClient asRenamedUser = new TestApiClient(port);
        assertThat(asRenamedUser.loginRaw("edit-flow-renamed-" + unique + "@kcpcbandhani.local", "OriginalPassw0rd!")
                .statusCode()).isEqualTo(401);
    }

    @Test
    void editingOneUserNeverAffectsAnother() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String editedId = createEmployee(ceo, "Edit Flow Target", "edit-flow-target-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID);
        createEmployee(ceo, "Edit Flow Bystander", "edit-flow-bystander-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID);

        assertThat(editUser(ceo, editedId, "Edit Flow Target Renamed", "edit-flow-target-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID, true, "isolation check").statusCode()).isEqualTo(200);

        // No single-user REST GET exists in this API surface for a plain profile read; verify via
        // the admin list page instead - the bystander's id is unused above deliberately, its row
        // simply must stay exactly as created.
        String usersPage = ceo.get("/app/admin/users").body();
        assertThat(usersPage).contains("Edit Flow Target Renamed");
        assertThat(usersPage).contains("Edit Flow Bystander"); // untouched, original name still present
        assertThat(usersPage).doesNotContain("Edit Flow Bystander Renamed");
    }

    @Test
    void blankFullNameIsRejected() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String userId = createEmployee(ceo, "Edit Flow Blank Name", "edit-flow-blankname-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID);

        HttpResponse<String> response = editUser(ceo, userId, "   ",
                "edit-flow-blankname-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID, true, "test");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Full Name is mandatory");
    }

    @Test
    void invalidEmailFormatIsRejected() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String userId = createEmployee(ceo, "Edit Flow Bad Email", "edit-flow-bademail-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID);

        HttpResponse<String> response = editUser(ceo, userId, "Edit Flow Bad Email", "not-a-valid-email",
                CAMERA_PERSON_ROLE_ID, true, "test");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("valid Email is mandatory");
    }

    @Test
    void blankReasonIsRejected() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String userId = createEmployee(ceo, "Edit Flow Blank Reason", "edit-flow-blankreason-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID);

        HttpResponse<String> response = editUser(ceo, userId, "Edit Flow Blank Reason",
                "edit-flow-blankreason-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID, true, "");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("reason is mandatory");
    }

    @Test
    void duplicateEmailIsRejectedAndOriginalDataUntouched() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String existingEmail = "edit-flow-existing-" + unique + "@kcpcbandhani.local";
        createEmployee(ceo, "Edit Flow Existing", existingEmail, CAMERA_PERSON_ROLE_ID);
        String targetId = createEmployee(ceo, "Edit Flow Duplicate Target",
                "edit-flow-dup-target-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);

        HttpResponse<String> response = editUser(ceo, targetId, "Attempted Rename", existingEmail,
                CAMERA_PERSON_ROLE_ID, true, "trying a duplicate email");
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("CONFLICT_DUPLICATE_SUBMISSION");

        // Nothing changed on the target - old name/email still what they were before the attempt.
        String usersPage = ceo.get("/app/admin/users").body();
        assertThat(usersPage).contains("Edit Flow Duplicate Target");
        assertThat(usersPage).doesNotContain("Attempted Rename");
    }

    @Test
    void nonExistentBusinessRoleIsRejected() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String userId = createEmployee(ceo, "Edit Flow Bad Role", "edit-flow-badrole-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID);

        HttpResponse<String> response = editUser(ceo, userId, "Edit Flow Bad Role",
                "edit-flow-badrole-" + unique + "@kcpcbandhani.local", java.util.UUID.randomUUID().toString(), true, "test");
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void nonCeoCannotEditAnotherUsersProfile() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String targetEmail = "edit-flow-unauth-target-" + unique + "@kcpcbandhani.local";
        String targetId = createEmployee(ceo, "Edit Flow Unauth Target", targetEmail, CAMERA_PERSON_ROLE_ID);
        String attackerEmail = "edit-flow-attacker-" + unique + "@kcpcbandhani.local";
        createEmployee(ceo, "Edit Flow Attacker", attackerEmail, CAMERA_PERSON_ROLE_ID);

        TestApiClient attacker = new TestApiClient(port);
        attacker.login(attackerEmail, "OriginalPassw0rd!");

        HttpResponse<String> response = editUser(attacker, targetId, "Hacked Name", "hacked-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID, true, "unauthorized attempt");
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("PERM_ACCESS_CLASS_DENIED");

        // Target completely untouched by the rejected attempt.
        String usersPage = ceo.get("/app/admin/users").body();
        assertThat(usersPage).contains("Edit Flow Unauth Target");
        assertThat(usersPage).doesNotContain("Hacked Name");
    }
}
