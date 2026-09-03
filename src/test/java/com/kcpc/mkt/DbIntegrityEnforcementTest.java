package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.support.TestApiClient;
import com.kcpc.mkt.workflow.repository.WorkHoldRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB-001: proves the Postgres-level (not just application-level) append-only guarantees added in
 * {@code V13__db_privilege_split_and_truncate_guards.sql} - hard DELETE rejection on
 * {@code work_hold_records} (ERD-CON-062 rule 9, not covered by V9's UPDATE-lifecycle trigger)
 * and the TRUNCATE guard on append-only tables (ERD-CON-058, which V12's row-level UPDATE/DELETE
 * triggers structurally cannot cover - Postgres BEFORE triggers never fire on TRUNCATE).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DbIntegrityEnforcementTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    WorkHoldRecordRepository workHoldRecordRepository;
    @Autowired
    DataSource dataSource;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    @Test
    void workHoldRecordHardDeleteIsRejectedAtTheDatabaseLevel() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String camEmail = "dbint-camera-" + unique + "@kcpcbandhani.local";
        JsonNode camUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"DbInt Camera\",\"email\":\"" + camEmail
                        + "\",\"password\":\"Passw0rd!\",\"businessRoleId\":\""
                        + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"db integrity test fixture\"}");
        String camId = camUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"db integrity test fixture grant\"}");
        String pubEmail = "dbint-pub-" + unique + "@kcpcbandhani.local";
        JsonNode pubUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"DbInt Publisher\",\"email\":\"" + pubEmail
                        + "\",\"password\":\"Passw0rd!\",\"businessRoleId\":\""
                        + PUBLISHER_ROLE_ID + "\",\"creationReason\":\"db integrity test fixture\"}");
        String pubId = pubUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"db integrity test fixture grant\"}");
        // ENG-043: "Start Shoot" now requires the actively assigned Cameraperson, not CEO native authority.
        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"DB Integrity Hold Test " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field and transitions straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        String liveDate = LocalDate.now().plusDays(10).toString();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/dbint-" + unique + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\"}],"
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}}");

        Idea ideaEntity = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(ideaEntity).orElseThrow();
        String contentPlanId = plan.getId().toString();

        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");

        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/hold", "{\"reason\":\"DB integrity test hold.\"}");

        String holdRecordId = workHoldRecordRepository.findByWorkflowInstanceAndResumedAtIsNull(plan.getWorkflowInstance())
                .orElseThrow().getId().toString();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM work_hold_records WHERE hold_record_id = ?::uuid", holdRecordId))
                .hasMessageContaining("hard DELETE is prohibited");

        Integer stillPresent = jdbc.queryForObject(
                "SELECT count(*) FROM work_hold_records WHERE hold_record_id = ?::uuid", Integer.class, holdRecordId);
        assertThat(stillPresent).isEqualTo(1);
    }

    /**
     * ENG-050: V18 dropped stage_comments' append-only UPDATE-reject trigger (own-comment edit/soft-
     * delete now needs a real UPDATE) but deliberately left the DELETE-reject trigger in place - a
     * hard DELETE must still be impossible at the DB level even though the row is otherwise mutable.
     */
    @Test
    void stageCommentHardDeleteIsStillRejectedAtTheDatabaseLevelAfterEng050() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "dbint-stagecmt-" + unique + "@kcpcbandhani.local";
        JsonNode camUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"DbInt Stage Cmt\",\"email\":\"" + camEmail
                        + "\",\"password\":\"Passw0rd!\",\"businessRoleId\":\""
                        + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"db integrity test fixture\"}");
        String camId = camUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"db integrity test fixture grant\"}");
        String pubEmail = "dbint-stagecmt-pub-" + unique + "@kcpcbandhani.local";
        JsonNode pubUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"DbInt Stage Cmt Publisher\",\"email\":\"" + pubEmail
                        + "\",\"password\":\"Passw0rd!\",\"businessRoleId\":\""
                        + PUBLISHER_ROLE_ID + "\",\"creationReason\":\"db integrity test fixture\"}");
        String pubId = pubUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"db integrity test fixture grant\"}");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"DB Integrity Stage Comment Test " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        String liveDate = LocalDate.now().plusDays(10).toString();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/dbint-stagecmt-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}}");
        Idea ideaEntity = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(ideaEntity).orElseThrow();
        String contentPlanId = plan.getId().toString();

        var posted = ceo.postFormAjax("/app/deliverables/" + contentPlanId + "/shooting/comments",
                java.util.Map.of("commentText", "DB integrity comment"));
        String commentId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(posted.body())
                .get("commentId").asText();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM stage_comments WHERE comment_id = ?::uuid", commentId))
                .hasMessageContaining("append-only");

        Integer stillPresent = jdbc.queryForObject(
                "SELECT count(*) FROM stage_comments WHERE comment_id = ?::uuid", Integer.class, commentId);
        assertThat(stillPresent).isEqualTo(1);
    }

    @Test
    void truncateIsRejectedOnAppendOnlyTables() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE system_audit_log"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE workflow_transition_history"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE work_hold_records"))
                .hasMessageContaining("append-only");
    }
}
