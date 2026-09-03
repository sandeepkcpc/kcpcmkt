package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BRS-REQ-012 / ERD-CON-011: an Employee exercising a delegated review permission cannot decide
 * on their own submitted/prepared/executed work - covering both the forbidden self-decision and
 * the permitted decision by a different delegated reviewer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SelfReviewConflictTest {

    @LocalServerPort
    int port;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    @Test
    void delegatedEmployeeCannotApproveOwnIdea_butAnotherDelegatedReviewerCan() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String submitterEmail = "self-review-a-" + unique + "@kcpcbandhani.local";
        String otherReviewerEmail = "self-review-b-" + unique + "@kcpcbandhani.local";
        String submitterId = createUser(ceo, "Self Review A", submitterEmail);
        String otherReviewerId = createUser(ceo, "Self Review B", otherReviewerEmail);

        grantIdeaReviewPermission(ceo, submitterId);
        grantIdeaReviewPermission(ceo, otherReviewerId);

        TestApiClient submitter = new TestApiClient(port);
        submitter.login(submitterEmail, "Passw0rd!");
        JsonNode idea = submitter.postJson("/api/v1/ideas", "{\"title\":\"Self Review Conflict " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        // The submitter also holds delegated Idea Review authority, but cannot decide on their own idea.
        var selfAttempt = submitter.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":0.5,\"editorMark\":0.5,\"modelMark\":0.5}");
        assertThat(selfAttempt.statusCode()).isEqualTo(403);
        assertThat(selfAttempt.body()).contains("PERM_SELF_APPROVAL_PROHIBITED");

        // A different delegated reviewer may decide on it without conflict.
        String camId = createUser(ceo, "Self Review Cam", "self-review-cam-" + unique + "@kcpcbandhani.local");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"self-review test fixture grant\"}");
        JsonNode pubUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Self Review Pub\",\"email\":\"self-review-pub-" + unique + "@kcpcbandhani.local\","
                        + "\"password\":\"Passw0rd!\",\"businessRoleId\":\"" + PUBLISHER_ROLE_ID
                        + "\",\"creationReason\":\"self-review test fixture\"}");
        String pubId = pubUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"self-review test fixture grant\"}");
        TestApiClient otherReviewer = new TestApiClient(port);
        otherReviewer.login(otherReviewerEmail, "Passw0rd!");
        // Workflow redesign: approval carries every former Planning field and transitions straight
        // to Shoot Assigned (SA), never PL/PLRV/PLAP.
        var decision = otherReviewer.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":0.5,\"editorMark\":0.5,\"modelMark\":0.5,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + java.time.LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/self-review-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pubId + "\"]}}");
        assertThat(decision.get("status").asText()).isEqualTo("SA");
    }

    private String createUser(TestApiClient ceo, String fullName, String email) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"self-review test fixture\"}");
        return response.get("userId").asText();
    }

    private void grantIdeaReviewPermission(TestApiClient ceo, String granteeUserId) throws Exception {
        var response = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + granteeUserId + "\",\"permission\":\"PERM_01_IDEA_REVIEW\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"self-review test fixture\"}");
        assertThat(response.statusCode()).isEqualTo(201);
    }
}
