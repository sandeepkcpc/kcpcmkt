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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assignee-picker "N Active Tasks" must mean exactly what Team Workload's Assignee Load already
 * means by it: ONE DISTINCT Content Plan per employee. An employee holding several roles on the
 * SAME Content ID (Model + Cameraperson on one shoot) is 1 active task, not 2.
 *
 * <p>Three defects this suite guards against, each of which used to make a picker disagree with
 * Team Workload about the same person:
 * <ul>
 *   <li>the queries counted ROWS, so two rows for one employee on one plan read as 2;</li>
 *   <li>each picker counted only its own stage in isolation, so the same employee showed a
 *       different number in the Model picker than in the Cameraperson picker;</li>
 *   <li>the Model picker used the broad "not yet closed out" set instead of the SHOOT window, so a
 *       Model kept showing tasks long after the shoot was done.</li>
 * </ul>
 *
 * <p>Drives the real HTTP surface, and reads the number out of the rendered picker markup, so what
 * is asserted is what a user actually sees.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AssigneeWorkloadCountDedupTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String roleId) throws Exception {
        return ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"workload dedup fixture\"}")
                .get("userId").asText();
    }

    private void grant(TestApiClient ceo, String userId, String permission) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permission + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"workload dedup fixture grant\"}");
    }

    private String publisher(TestApiClient ceo, long unique, String label) throws Exception {
        String id = createUser(ceo, "Dedup Pub " + label + " " + unique,
                "dedup-pub-" + label + "-" + unique + "@kcpcbandhani.local", PUBLISHER_ROLE_ID);
        grant(ceo, id, "PERM_08_PUBLISHING_EXECUTION");
        return id;
    }

    /** An approved plan at Shoot Assigned, with the given cameraperson and (optionally) the given
     *  talent/Model attached to the SAME Content Plan. */
    private String planAtShoot(TestApiClient ceo, long unique, String label, String camId, String talentId)
            throws Exception {
        String pubId = publisher(ceo, unique, label);
        String ideaId = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"Dedup " + label + " " + unique + "\"}").get("ideaId").asText();
        String talent = talentId == null ? "" : ",\"talentUserIds\":[\"" + talentId + "\"]";
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"HIGH\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/dedup-" + label + "-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]" + talent + "}}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");
        Idea idea = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        return plan.getId().toString();
    }

    /** An observer plan: its own Shoot Team is a throwaway cameraperson, so the "who can I assign"
     *  checklist on it lists every OTHER eligible candidate with their current count. */
    private String observerPlan(TestApiClient ceo, long unique) throws Exception {
        String observerCam = createUser(ceo, "Dedup Observer Cam " + unique,
                "dedup-observer-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grant(ceo, observerCam, "PERM_18_SHOOT_EXECUTION");
        return planAtShoot(ceo, unique, "OBS", observerCam, null);
    }

    /** The Idea Review & Planning form for a fresh Pending-Approval idea - the one screen that
     *  renders the Model(s), Camera Person(s), Editor(s) and Publisher(s) pickers together. */
    private String planningFormHtml(TestApiClient ceo, long unique) throws Exception {
        String ideaId = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"Dedup Observer " + unique + "\"}").get("ideaId").asText();
        return ceo.get("/app/reviews?tab=ideas&ideaId=" + ideaId).body();
    }

    /** The "(N Active Task(s))" label rendered immediately after a candidate's name. */
    private static String label(String html, String candidateName) {
        int nameIndex = html.indexOf(candidateName);
        assertThat(nameIndex).as("candidate '%s' present in rendered page", candidateName).isGreaterThanOrEqualTo(0);
        int open = html.indexOf('(', nameIndex);
        int close = html.indexOf(')', open);
        assertThat(open).isGreaterThanOrEqualTo(0);
        assertThat(close).isGreaterThan(open);
        return html.substring(open + 1, close);
    }

    // --- 1. same employee + same Content ID + multiple roles = 1 ------------------------------------
    @Test
    void sameEmployeeInTwoRolesOnOneContentIdCountsAsOneActiveTask() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        // One person who is both the Cameraperson AND the Model on the same Content Plan.
        String rahul = createUser(ceo, "Dedup Rahul " + unique,
                "dedup-rahul-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grant(ceo, rahul, "PERM_18_SHOOT_EXECUTION");
        planAtShoot(ceo, unique, "DUAL", rahul, rahul);

        String html = ceo.get("/app/deliverables/" + observerPlan(ceo, unique + 1)).body();
        assertThat(label(html, "Dedup Rahul " + unique))
                .as("Model + Cameraperson on ONE Content ID is one unit of work, not two")
                .isEqualTo("1 Active Task");
    }

    // --- 2. same employee + different Content IDs = one per distinct Content ID ---------------------
    @Test
    void sameEmployeeOnThreeContentIdsCountsThree() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String cam = createUser(ceo, "Dedup Multi " + unique,
                "dedup-multi-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grant(ceo, cam, "PERM_18_SHOOT_EXECUTION");
        planAtShoot(ceo, unique, "M1", cam, null);
        planAtShoot(ceo, unique, "M2", cam, null);
        // The third one also has this person as the Model - it is still ONE more Content ID.
        planAtShoot(ceo, unique, "M3", cam, cam);

        String html = ceo.get("/app/deliverables/" + observerPlan(ceo, unique + 1)).body();
        assertThat(label(html, "Dedup Multi " + unique))
                .as("three distinct Content IDs, one of them dual-role, is 3").isEqualTo("3 Active Tasks");
    }

    // --- 3. different employees on the same Content ID = 1 each ------------------------------------
    @Test
    void differentEmployeesOnOneContentIdEachGetOne() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String cam = createUser(ceo, "Dedup CamOnly " + unique,
                "dedup-camonly-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grant(ceo, cam, "PERM_18_SHOOT_EXECUTION");
        String model = createUser(ceo, "Dedup ModelOnly " + unique,
                "dedup-modelonly-" + unique + "@kcpcbandhani.local", MODEL_ROLE_ID);
        planAtShoot(ceo, unique, "SPLIT", cam, model);

        // The Planning form renders the Cameraperson and Model pickers side by side.
        String html = planningFormHtml(ceo, unique + 1);
        assertThat(label(html, "Dedup CamOnly " + unique))
                .as("the Cameraperson has one active task").isEqualTo("1 Active Task");
        assertThat(label(html, "Dedup ModelOnly " + unique))
                .as("the Model on the SAME Content ID also has one - each person is counted once")
                .isEqualTo("1 Active Task");
    }

    // --- 4. every picker agrees about the same person ----------------------------------------------
    @Test
    void allPickersOnOnePageShowTheSameNumberForTheSamePerson() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String rahul = createUser(ceo, "Dedup Everywhere " + unique,
                "dedup-everywhere-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grant(ceo, rahul, "PERM_18_SHOOT_EXECUTION");
        planAtShoot(ceo, unique, "EVERY", rahul, rahul);

        String html = ceo.get("/app/deliverables/" + observerPlan(ceo, unique + 1)).body();
        String name = "Dedup Everywhere " + unique;
        // Every rendered occurrence of this candidate (Shoot picker, Model picker, Lead dropdown,
        // ...) must carry the identical label - a picker-specific tally would show a different one.
        int from = 0, seen = 0;
        while (true) {
            int idx = html.indexOf(name, from);
            if (idx < 0) {
                break;
            }
            int open = html.indexOf('(', idx);
            int close = html.indexOf(')', open);
            if (open >= 0 && close > open && close - open < 30) {
                assertThat(html.substring(open + 1, close))
                        .as("every picker must agree about %s", name).isEqualTo("1 Active Task");
                seen++;
            }
            from = idx + name.length();
        }
        assertThat(seen).as("the candidate must be rendered in at least one picker").isGreaterThan(0);
    }

    // --- 5. a Model's count is gated by the SHOOT window, like Team Workload's own Model row -------
    @Test
    void aModelStopsCountingOnceTheShootStageIsPast() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String camEmail = "dedup-pastcam-" + unique + "@kcpcbandhani.local";
        String cam = createUser(ceo, "Dedup PastCam " + unique, camEmail, CAMERA_PERSON_ROLE_ID);
        grant(ceo, cam, "PERM_18_SHOOT_EXECUTION");
        String model = createUser(ceo, "Dedup PastModel " + unique,
                "dedup-pastmodel-" + unique + "@kcpcbandhani.local", MODEL_ROLE_ID);
        String planId = planAtShoot(ceo, unique, "PAST", cam, model);

        assertThat(label(planningFormHtml(ceo, unique + 1), "Dedup PastModel " + unique))
                .as("while the plan is in the Shoot window the Model has one active task")
                .isEqualTo("1 Active Task");

        // Drive the plan past Shoot (SA -> ... -> EA).
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        String editor = createUser(ceo, "Dedup Editor " + unique,
                "dedup-editor-" + unique + "@kcpcbandhani.local", VIDEO_EDITOR_ROLE_ID);
        grant(ceo, editor, "PERM_19_EDIT_EXECUTION"); // Shoot Review Approve folds in the Edit team
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"],"
                        + "\"editorUserIds\":[\"" + editor + "\"],\"leadEditorUserId\":\"" + editor + "\"}");
        assertThat(approved.get("status").asText()).isEqualTo("EA");

        assertThat(label(planningFormHtml(ceo, unique + 2), "Dedup PastModel " + unique))
                .as("a Model's work is tied to the Shoot stage - past it, the task no longer counts")
                .isEqualTo("0 Active Tasks");
    }
}
