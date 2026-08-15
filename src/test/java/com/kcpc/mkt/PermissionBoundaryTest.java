package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Continuation-Prompt Section 6: permission-denial and workflow-guard negative paths not covered
 * by the golden path or the domain-specific flow tests - an Employee with no delegated authority
 * attempting a governed write (403), a governed action attempted out of workflow-state order
 * (409), and immediate session revocation on account deactivation (KCPC-MKT-R3.5-DEVELOPMENT-
 * HANDOFF.md "Deactivation revokes active tokens immediately").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PermissionBoundaryTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";

    @Test
    void plainEmployeeCannotDecideIdeaReview() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String email = "e2e-perm-cam-" + unique + "@kcpcbandhani.local";
        createUser(ceo, "Perm Camera", email, CAMERA_PERSON_ROLE_ID);

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Perm Boundary " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        TestApiClient employee = new TestApiClient(port);
        employee.login(email, "Passw0rd!");
        HttpResponse<String> attempt = employee.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        assertThat(attempt.statusCode()).isEqualTo(403);
    }

    @Test
    void plainEmployeeCannotExecutePlanningActions() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String email = "e2e-perm-plan-" + unique + "@kcpcbandhani.local";
        createUser(ceo, "Perm Planner", email, CAMERA_PERSON_ROLE_ID);

        String contentPlanId = approveIdeaAndGetContentPlanId(ceo, "Perm Planning " + unique);

        // The employee holds no PERM_02_PLANNING_EXECUTION grant - every planning write is forbidden.
        TestApiClient employee = new TestApiClient(port);
        employee.login(email, "Passw0rd!");
        HttpResponse<String> scheduleAttempt = employee.post("/api/v1/content-plans/" + contentPlanId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        assertThat(scheduleAttempt.statusCode()).isEqualTo(403);

        HttpResponse<String> parametersAttempt = employee.post("/api/v1/content-plans/" + contentPlanId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/forbidden\"}");
        assertThat(parametersAttempt.statusCode()).isEqualTo(403);
    }

    @Test
    void workflowGuardRejectsOutOfOrderShootingAndPublishingStarts() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String contentPlanId = approveIdeaAndGetContentPlanId(ceo, "Guard Order " + unique);
        JsonNode planBeforeSchedule = ceo.getJson("/api/v1/content-plans/" + contentPlanId);
        assertThat(planBeforeSchedule.get("status").asText()).isEqualTo("PL");

        // Shooting cannot start before Planning Review has been approved (SA has not been reached).
        HttpResponse<String> earlyShootStart = ceo.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");
        assertThat(earlyShootStart.statusCode()).isEqualTo(409);

        // Publishing cannot start before Ready for Publishing (RFP) has been reached.
        HttpResponse<String> earlyPublishStart = ceo.post("/api/v1/content-plans/" + contentPlanId + "/publishing/start", "");
        assertThat(earlyPublishStart.statusCode()).isEqualTo(409);
    }

    @Test
    void deactivatingAUserRevokesTheirSessionImmediately() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String email = "e2e-deactivate-" + unique + "@kcpcbandhani.local";
        String userId = createUser(ceo, "Deactivate Me", email, CAMERA_PERSON_ROLE_ID);

        TestApiClient employee = new TestApiClient(port);
        JsonNode loginResult = employee.login(email, "Passw0rd!");
        assertThat(loginResult.has("userId")).isTrue();
        HttpResponse<String> beforeDeactivation = employee.get("/api/v1/ideas");
        assertThat(beforeDeactivation.statusCode()).isEqualTo(200);

        HttpResponse<String> deactivateResponse = ceo.post("/api/v1/admin/users/" + userId + "/deactivate",
                "{\"reason\":\"e2e permission boundary test\"}");
        assertThat(deactivateResponse.statusCode()).isEqualTo(200);

        // The already-issued session cookie must be rejected immediately, not just future logins.
        HttpResponse<String> afterDeactivation = employee.get("/api/v1/ideas");
        assertThat(afterDeactivation.statusCode()).isEqualTo(401);

        HttpResponse<String> freshLoginAttempt = employee.loginRaw(email, "Passw0rd!");
        assertThat(freshLoginAttempt.statusCode()).isEqualTo(401);
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"e2e test fixture\"}");
        return response.get("userId").asText();
    }

    private String approveIdeaAndGetContentPlanId(TestApiClient ceo, String title) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        Idea ideaEntity = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(ideaEntity).orElseThrow();
        return plan.getId().toString();
    }
}
