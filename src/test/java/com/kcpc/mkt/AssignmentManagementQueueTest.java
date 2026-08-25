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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for two related permission-driven-workflow additions:
 * <ul>
 *   <li>The Shoot tab (Content Detail) is now the single canonical, immediately-effective UI for
 *       Shoot Assignment management, reachable by a PERM_04-only delegated employee (not just
 *       canPlanningExecute+PERM_04) - see DeliverableMvcController#view / the Shoot tab panel in
 *       deliverable-detail.jsp.</li>
 *   <li>My Work -&gt; Assignment Management is an actionable queue (AssignmentManagementQueueService):
 *       a Content ID appears only while the viewer can currently perform SOME assignment-management
 *       action on it, never as a historical/broader list.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AssignmentManagementQueueTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String HR_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000003";
    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";

    @Test
    void perm04OnlyHrCanReachDeliverableDetailAndMyWorkWithoutAnyExecutionPermission() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "aq-hr-reach-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(ceo, "AQ Reach HR", email, HR_MANAGER_ROLE_ID);
        grant(ceo, hrId, "PERM_04_SHOOT_ASSIGNMENT");
        String planId = approveIdeaAndGetContentPlanId(ceo, "AQ Reach Flow " + unique);

        TestApiClient hr = loginNewClient(email);
        assertThat(hr.get("/app/my-work").statusCode()).isEqualTo(200);
        assertThat(hr.get("/app/deliverables/" + planId).statusCode()).isEqualTo(200);
    }

    @Test
    void hrWithoutAnyAssignmentOrExecutionPermissionCannotReachDeliverableDetail() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "aq-hr-noreach-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(ceo, "AQ NoReach HR", email, HR_MANAGER_ROLE_ID);
        String planId = approveIdeaAndGetContentPlanId(ceo, "AQ NoReach Flow " + unique);

        TestApiClient hr = loginNewClient(email);
        assertThat(hr.get("/app/deliverables/" + planId).statusCode()).isEqualTo(302);
    }

    @Test
    void shootQueueShowsPlanDuringPlanningForPerm04HolderAndDropsOutOncePastPlanningWithoutPerm11() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "aq-hr-queue-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(ceo, "AQ Queue HR", email, HR_MANAGER_ROLE_ID);
        grant(ceo, hrId, "PERM_04_SHOOT_ASSIGNMENT");
        String planId = approveIdeaAndGetContentPlanId(ceo, "AQ Queue Flow " + unique);
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();

        TestApiClient hr = loginNewClient(email);
        HttpResponse<String> myWork = hr.get("/app/my-work");
        assertThat(myWork.statusCode()).isEqualTo(200);
        assertThat(myWork.body()).as("Appears in the Shoot queue while status is Planning and PERM_04 is held")
                .contains(plan.getContentId()).contains("Set Up Shoot Team");

        // Assign a Cameraperson (still status PL) - the row stays, but the action label changes
        // since a team already exists.
        String camId = createUser(ceo, "AQ Queue Cam", "aq-queue-cam-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grant(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + camId + "\"}");
        HttpResponse<String> myWorkAfterAssign = hr.get("/app/my-work");
        assertThat(myWorkAfterAssign.body()).contains(plan.getContentId()).contains("Manage Assignment");

        // Move the plan past Planning (Planning Review submit + approve) - PERM_04 alone no longer
        // authorizes any Shoot assignment action, so the row must drop out of the PERM_04 holder's
        // queue automatically (never a hard-coded status filter - see
        // AssignmentManagementQueueService).
        preparePlanningAndSubmit(ceo, planId, unique);
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/decision",
                "{\"approve\":true}");

        HttpResponse<String> myWorkAfterApproval = hr.get("/app/my-work");
        assertThat(myWorkAfterApproval.body()).as("PERM_04-only holder loses this row once the Planning window has closed")
                .doesNotContain(plan.getContentId() + "</td>");
    }

    @Test
    void shootQueueShowsPlanForPerm11HolderOncePastPlanningWindow() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "aq-hr-reassign-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(ceo, "AQ Reassign HR", email, HR_MANAGER_ROLE_ID);
        grant(ceo, hrId, "PERM_11_REASSIGN");
        String camId = createUser(ceo, "AQ Reassign Cam", "aq-reassign-cam-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grant(ceo, camId, "PERM_18_SHOOT_EXECUTION");

        String planId = approveIdeaAndGetContentPlanId(ceo, "AQ Reassign Flow " + unique);
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + camId + "\"}");

        TestApiClient hr = loginNewClient(email);
        // Still status PL - PERM_11 alone (no PERM_04) does not authorize the Shoot
        // initial-assignment window, so the row must NOT appear in the SHOOT queue yet (PERM_11 has
        // no stage gate of its own, so it may legitimately still appear in the Edit reassign queue -
        // scope this assertion to the Shoot section specifically, not the whole page).
        assertThat(shootSection(hr.get("/app/my-work").body())).doesNotContain(plan.getContentId() + "</td>");

        preparePlanningAndSubmit(ceo, planId, unique);
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");

        HttpResponse<String> myWorkAfterApproval = hr.get("/app/my-work");
        assertThat(shootSection(myWorkAfterApproval.body())).as("PERM_11 holder now sees it - status moved past the PERM_04 initial-setup window")
                .contains(plan.getContentId()).contains("Reassign Team");
    }

    private String shootSection(String body) {
        int start = body.indexOf("Shoot Assignment Management");
        int end = body.indexOf("Edit Assignment Management");
        assertThat(start).as("Shoot Assignment Management section present").isGreaterThanOrEqualTo(0);
        assertThat(end).as("Edit Assignment Management section present").isGreaterThan(start);
        return body.substring(start, end);
    }

    @Test
    void editQueueMirrorsShootQueueForPerm06() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "aq-hr-edit-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(ceo, "AQ Edit HR", email, HR_MANAGER_ROLE_ID);
        grant(ceo, hrId, "PERM_06_EDIT_ASSIGNMENT");

        String camId = createUser(ceo, "AQ Edit Flow Cam", "aq-edit-flow-cam-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grant(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        String planId = approveIdeaAndGetContentPlanId(ceo, "AQ Edit Flow " + unique);
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();

        TestApiClient hr = loginNewClient(email);
        assertThat(hr.get("/app/my-work").body()).as("Not yet in the Edit queue - Shoot hasn't been approved")
                .doesNotContain(plan.getContentId() + "</td>");

        ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + camId + "\"}");
        preparePlanningAndSubmit(ceo, planId, unique);
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");
        TestApiClient cam = loginNewClient("aq-edit-flow-cam-" + unique + "@kcpcbandhani.local");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"]}");

        HttpResponse<String> myWork = hr.get("/app/my-work");
        assertThat(myWork.body()).as("Now Shoot-Approved - the Edit queue picks it up for the PERM_06 holder")
                .contains(plan.getContentId()).contains("Set Up Edit Team");
    }

    @Test
    void goToShootSetupLinkFromQueueLandsDirectlyOnTheShootTab() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String planId = approveIdeaAndGetContentPlanId(ceo, "AQ Tab Activation Flow " + unique);

        HttpResponse<String> page = ceo.get("/app/deliverables/" + planId + "?tab=shoot");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("class=\"my-work-tab active\" data-tab=\"shoot\"");
        assertThat(page.body()).contains("data-tab-panel=\"shoot\">");
        assertThat(page.body()).doesNotContain("class=\"my-work-tab active\" data-tab=\"overview\"");
    }

    /**
     * Content Detail tab visibility is permission-scoped, not a fixed Overview..Timeline set for
     * every viewer: an HR employee with only PERM_04 (Shoot Assignment) + PERM_06 (Edit Assignment)
     * sees Overview | Shoot | Edit and nothing else - Planning/Publishing/Performance/Timeline stay
     * hidden until she holds a corresponding permission. The lifecycle stepper (separate from these
     * tabs) is untouched by this and still renders in full for every viewer.
     */
    @Test
    void hrWithOnlyShootAndEditAssignmentPermissionsSeesOnlyOverviewShootEditTabs() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "aq-hr-tabs-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(ceo, "AQ Tabs HR", email, HR_MANAGER_ROLE_ID);
        grant(ceo, hrId, "PERM_04_SHOOT_ASSIGNMENT");
        grant(ceo, hrId, "PERM_06_EDIT_ASSIGNMENT");
        String planId = approveIdeaAndGetContentPlanId(ceo, "AQ Tabs Flow " + unique);

        TestApiClient hr = loginNewClient(email);
        HttpResponse<String> page = hr.get("/app/deliverables/" + planId);
        assertThat(page.statusCode()).isEqualTo(200);
        String body = page.body();
        assertThat(body).contains("data-tab=\"overview\">Overview</button>")
                .contains("data-tab=\"shoot\">Shoot</button>")
                .contains("data-tab=\"edit\">Edit</button>");
        assertThat(body).doesNotContain("data-tab=\"planning\">Planning</button>")
                .doesNotContain("data-tab=\"publishing\">Publishing</button>")
                .doesNotContain("data-tab=\"performance\">Performance</button>")
                .doesNotContain("data-tab=\"timeline\">Timeline</button>");
        // The lifecycle stepper is a separate, always-full read-only element - unaffected by tab
        // scoping (still shows all six stage labels regardless of which tabs are visible).
        assertThat(body).contains("content-detail-step-label\">Planning</span>")
                .contains("content-detail-step-label\">Publishing</span>");
    }

    @Test
    void ceoStillSeesEveryTabAndBackToPipeline() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String planId = approveIdeaAndGetContentPlanId(ceo, "AQ CEO Tabs Flow " + unique);

        HttpResponse<String> page = ceo.get("/app/deliverables/" + planId);
        String body = page.body();
        assertThat(body).contains("data-tab=\"planning\">Planning</button>")
                .contains("data-tab=\"shoot\">Shoot</button>")
                .contains("data-tab=\"edit\">Edit</button>")
                .contains("data-tab=\"publishing\">Publishing</button>")
                .contains("data-tab=\"performance\">Performance</button>")
                .contains("data-tab=\"timeline\">Timeline</button>");
        assertThat(body).contains("Back to Pipeline").doesNotContain("Back to My Work");
    }

    /** Caller-aware back navigation: a delegated employee never gets a link to Pipeline, which she can't open. */
    @Test
    void delegatedEmployeeGetsBackToMyWorkNotBackToPipeline() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "aq-hr-back-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(ceo, "AQ Back HR", email, HR_MANAGER_ROLE_ID);
        grant(ceo, hrId, "PERM_04_SHOOT_ASSIGNMENT");
        String planId = approveIdeaAndGetContentPlanId(ceo, "AQ Back Flow " + unique);

        TestApiClient hr = loginNewClient(email);
        HttpResponse<String> page = hr.get("/app/deliverables/" + planId);
        assertThat(page.body()).contains("Back to My Work").doesNotContain("Back to Pipeline");
    }

    /** Operational terminology in the Shoot Assignment Management picker is permission-model
     *  language (Shoot Assignee), not a Business-Role assumption ("Cameraperson") - any Business
     *  Role can be PERM_18-eligible now. */
    @Test
    void shootAssignmentManagementUsesShootAssigneeTerminologyNotCameraperson() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String planId = approveIdeaAndGetContentPlanId(ceo, "AQ Terminology Flow " + unique);

        HttpResponse<String> page = ceo.get("/app/deliverables/" + planId + "?tab=shoot");
        String body = page.body();
        assertThat(body).contains(">Shoot Assignee(s)<")
                .contains("Search eligible shoot assignee...")
                .contains(">Assign Shoot Team<");
        // Scoped to the Shoot Assignment Management block itself - the page's separate Action
        // Center Reassign form (always in the DOM for CEO/MM, unrelated to this block) still says
        // "Search cameraperson..." today and is out of scope for this specific terminology fix.
        int start = body.indexOf("Shoot Assignment Management");
        int end = body.indexOf("Shoot Instructions", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String shootAssignmentBlock = body.substring(start, end);
        assertThat(shootAssignmentBlock).doesNotContain(">Cameraperson(s)<").doesNotContain("Search cameraperson...")
                .doesNotContain(">Assign Cameraperson(s)<");
    }

    // ------------------------------------------------------------------ helpers

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private TestApiClient loginNewClient(String email) throws Exception {
        TestApiClient client = new TestApiClient(port);
        client.login(email, "Passw0rd!");
        return client;
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"assignment management queue test fixture\"}");
        return response.get("userId").asText();
    }

    private void grant(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        HttpResponse<String> resp = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"assignment management queue test fixture grant\"}");
        if (resp.statusCode() != 201) {
            throw new IllegalStateException("Grant failed: " + resp.statusCode() + " " + resp.body());
        }
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

    private void preparePlanningAndSubmit(TestApiClient ceo, String planId, long unique) throws Exception {
        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + java.time.LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/aq-" + unique + "\"}");
        HttpResponse<String> submit = ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        if (submit.statusCode() != 200) {
            throw new IllegalStateException("Planning review submit failed: " + submit.statusCode() + " " + submit.body());
        }
    }
}
