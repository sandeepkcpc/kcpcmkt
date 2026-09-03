package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
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
 * "Reel Type" must never appear on an employee-facing Task Detail screen (Shoot/Edit/Publish) -
 * display-only change, the underlying data/field is untouched (still stored, still shown on the
 * CEO/MM shared shell at the same URL). Planned Output itself (Story/Post/Reel/Long Video) is
 * unaffected. Real HTTP, real Postgres, no mocking.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TaskDetailReelTypeHiddenTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String TARGET_INSTAGRAM_KCPC = "01926e3e-000a-7000-8000-000000000001";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String[] createUser(TestApiClient ceo, String label, String roleId, String permission, long unique)
            throws Exception {
        String email = "e2e-reeltype-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"ReelType " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"reel type hidden test fixture\"}");
        String userId = user.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permission + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"reel type hidden test grant\"}");
        return new String[] {userId, email};
    }

    /** Approves with a REEL output carrying a real Reel Type (SHORT), so there's something real to
     * hide - an empty/absent Reel Type wouldn't prove the row itself is gone, just that it was
     * empty. */
    private String reelOutputsJson() {
        return "\"outputs\":[{\"outputType\":\"REEL\",\"reelTypes\":[\"SHORT\"],"
                + "\"publicationTargetIds\":[\"" + TARGET_INSTAGRAM_KCPC + "\"]}]";
    }

    @Test
    void editTaskDetailNeverShowsReelTypeEvenWhenSet() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"RT Hidden Edit " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        // Direct Edit (Stages = Edit + Publishing) reaches EA directly from Idea Review approval.
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/reeltype-edit-" + unique + "\","
                        + reelOutputsJson() + ","
                        + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\","
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"],"
                        + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();

        TestApiClient editorClient = new TestApiClient(port);
        editorClient.login(editor[1], "Passw0rd!");
        String employeePage = editorClient.get("/app/deliverables/" + plan.getId()).body();

        assertThat(employeePage).contains("Edit Task");
        assertThat(employeePage).contains(">Planned Output<").contains("REEL");
        assertThat(employeePage).doesNotContain("Reel Type").doesNotContain("SHORT");

        // Same plan, CEO view: the underlying data is untouched - Reel Type still shows on the
        // shared shell, proving this was purely a display change on the employee screen.
        String ceoPage = ceo.get("/app/deliverables/" + plan.getId()).body();
        assertThat(ceoPage).contains("Reel Type");
    }

    @Test
    void shootTaskDetailNeverShowsReelType() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION", unique);
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"RT Hidden Shoot " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/reeltype-shoot-" + unique + "\","
                        + reelOutputsJson() + ","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();

        TestApiClient camClient = new TestApiClient(port);
        camClient.login(cam[1], "Passw0rd!");
        String page = camClient.get("/app/deliverables/" + plan.getId()).body();

        assertThat(page).contains(">Planned Output<").contains("REEL");
        assertThat(page).doesNotContain("Reel Type").doesNotContain("SHORT");
    }

    @Test
    void publishTaskDetailNeverShowsReelType() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] publisher = createUser(ceo, "pub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"RT Hidden Publish " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        // Direct Publishing (Stages = Publishing only) reaches RFP directly from Idea Review approval.
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/reeltype-pub-" + unique + "\","
                        + reelOutputsJson() + ","
                        + "\"publisherUserIds\":[\"" + publisher[0] + "\"],\"stages\":[\"PUBLISHING\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();

        TestApiClient pubClient = new TestApiClient(port);
        pubClient.login(publisher[1], "Passw0rd!");
        String page = pubClient.get("/app/deliverables/" + plan.getId()).body();

        assertThat(page).contains("Publish");
        assertThat(page).doesNotContain("Reel Type").doesNotContain("SHORT");
    }
}
