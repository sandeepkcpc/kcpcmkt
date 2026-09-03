package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.ContentPriority;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content Priority defaults to LOW when not explicitly provided on Idea Review approval - the
 * Planning form itself now pre-selects LOW (still changeable, see idea-detail.jsp/reviews-ideas.jspf),
 * and IdeaService#approve carries the same default server-side as a safety net for any caller
 * (including every pre-existing API/test caller) that omits the field entirely. An explicit value
 * is never overridden. Real HTTP, real Postgres, no mocking.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ContentPriorityDefaultTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String createCameraperson(TestApiClient ceo, long unique) throws Exception {
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Priority Cam\",\"email\":\"priority-cam-" + unique + "@kcpcbandhani.local\","
                        + "\"password\":\"Passw0rd!\",\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\","
                        + "\"creationReason\":\"content priority default test fixture\"}");
        String userId = user.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"content priority default test grant\"}");
        return userId;
    }

    private String createPublisher(TestApiClient ceo, long unique) throws Exception {
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Priority Pub\",\"email\":\"priority-pub-" + unique + "@kcpcbandhani.local\","
                        + "\"password\":\"Passw0rd!\",\"businessRoleId\":\"" + PUBLISHER_ROLE_ID + "\","
                        + "\"creationReason\":\"content priority default test fixture\"}");
        String userId = user.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"content priority default test grant\"}");
        return userId;
    }

    @Test
    void approvalWithoutContentPriorityDefaultsToLow() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String camId = createCameraperson(ceo, unique);
        String pubId = createPublisher(ceo, unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Priority Default " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        // No contentPriority field at all.
        HttpResponse<String> response = ceo.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/priority-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan plan = contentPlanRepository.findByIdea(
                ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow()).orElseThrow();
        assertThat(plan.getContentPriority()).isEqualTo(ContentPriority.LOW);
    }

    @Test
    void approvalWithExplicitContentPriorityIsNotOverridden() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String camId = createCameraperson(ceo, unique);
        String pubId = createPublisher(ceo, unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Priority Explicit " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        HttpResponse<String> response = ceo.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"HIGH\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/priority-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan plan = contentPlanRepository.findByIdea(
                ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow()).orElseThrow();
        assertThat(plan.getContentPriority()).isEqualTo(ContentPriority.HIGH);
    }

    @Test
    void planningFormsPreselectLowAsFirstOption() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Priority Form " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        String detailPage = ceo.get("/app/ideas/" + ideaId).body();
        int lowIndex = detailPage.indexOf("value=\"LOW\" selected");
        assertThat(lowIndex).as("LOW must be the pre-selected Priority option").isGreaterThanOrEqualTo(0);
    }
}
