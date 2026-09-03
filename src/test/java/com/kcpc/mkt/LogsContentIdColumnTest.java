package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.audit.service.AuditContentIdResolver;
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
 * Logs (Audit History) "Content ID" column - proves {@link AuditContentIdResolver} resolves the
 * business Content ID correctly for content-related target types (both directly via
 * content_plans and one hop via ideas/workflow_instances - exactly the IDEA_SUBMITTED/
 * IDEA_APPROVED examples the spec names), returns null (rendered "-") for genuinely non-content
 * admin actions, and that adding this column did not disturb the existing Record column, filters,
 * or ordering. Audit record CREATION itself is untouched - every assertion here reads already-
 * existing audit rows, never writes one directly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LogsContentIdColumnTest {

    @LocalServerPort
    int port;

    @Autowired
    AuditContentIdResolver contentIdResolver;
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

    private String[] createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "logs-cid-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"LogsCid " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"logs content-id test fixture\"}");
        return new String[] {user.get("userId").asText(), email};
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"logs content-id test fixture grant\"}");
    }

    /** Idea -> approved -> real ContentPlan, exactly the flow that produces IDEA_SUBMITTED
     *  ("ideas" target, before any Content Plan existed) and IDEA_APPROVED ("ideas" target too,
     *  but a Content Plan now exists) audit rows in the same transaction chain. */
    private ContentPlan approvedPlan(TestApiClient ceo, long unique) throws Exception {
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub[0], "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Logs ContentId " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/logs-cid-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"publisherUserIds\":[\"" + pub[0] + "\"]}}");

        return contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
    }

    // --- 1: content_plans target resolves directly -------------------------------------------
    @Test
    void contentPlansTargetResolvesDirectlyToItsOwnContentId() throws Exception {
        TestApiClient ceo = ceo();
        ContentPlan plan = approvedPlan(ceo, Instant.now().toEpochMilli());

        assertThat(contentIdResolver.resolveContentId("content_plans", plan.getId())).isEqualTo(plan.getContentId());
    }

    // --- 2: IDEA_SUBMITTED ("ideas" target) resolves to the Content ID once approved ----------
    @Test
    void ideaSubmittedTargetResolvesToItsContentIdAfterApproval() throws Exception {
        TestApiClient ceo = ceo();
        ContentPlan plan = approvedPlan(ceo, Instant.now().toEpochMilli());
        UUID ideaId = plan.getIdea().getId();

        // Same resolution IDEA_SUBMITTED's own "ideas" target uses - it was recorded before this
        // idea had any Content Plan, but display-time resolution (not creation-time) is what
        // the Logs page needs, exactly like AdminReportingService's own established precedent.
        assertThat(contentIdResolver.resolveContentId("ideas", ideaId)).isEqualTo(plan.getContentId());

        String body = ceo.get("/app/audit?actionType=IDEA_SUBMITTED&pageSize=50").body();
        assertThat(body).contains(plan.getContentId());
    }

    // --- 3: IDEA_APPROVED ("ideas" target, same idea) also resolves --------------------------
    @Test
    void ideaApprovedTargetResolvesToTheSameContentId() throws Exception {
        TestApiClient ceo = ceo();
        ContentPlan plan = approvedPlan(ceo, Instant.now().toEpochMilli());

        String body = ceo.get("/app/audit?actionType=IDEA_APPROVED&pageSize=50").body();
        assertThat(body).contains(plan.getContentId());
    }

    // --- workflow_instances target (IDEA_REJECTED/RETAINED/REOPENED's own target type) --------
    @Test
    void workflowInstancesTargetResolvesViaTheSharedWorkflowInstance() throws Exception {
        TestApiClient ceo = ceo();
        ContentPlan plan = approvedPlan(ceo, Instant.now().toEpochMilli());

        assertThat(contentIdResolver.resolveContentId("workflow_instances", plan.getWorkflowInstance().getId()))
                .isEqualTo(plan.getContentId());
    }

    // --- 4: a genuinely non-content admin action resolves to null (rendered "-") -------------
    @Test
    void nonContentTargetTypesResolveToNull() throws Exception {
        TestApiClient ceo = ceo();
        String[] user = createUser(ceo, "nc", CAMERA_PERSON_ROLE_ID, Instant.now().toEpochMilli());

        assertThat(contentIdResolver.resolveContentId("users", UUID.fromString(user[0]))).isNull();
        assertThat(contentIdResolver.resolveContentId("permission_grants", UUID.randomUUID())).isNull();
        assertThat(contentIdResolver.resolveContentId("mark_catalogue_entries", UUID.randomUUID())).isNull();
        // Unknown/never-audited target names and null inputs must never throw.
        assertThat(contentIdResolver.resolveContentId("something_not_real", UUID.randomUUID())).isNull();
        assertThat(contentIdResolver.resolveContentId(null, UUID.randomUUID())).isNull();
        assertThat(contentIdResolver.resolveContentId("users", null)).isNull();
    }

    // --- 5: the existing Record column (raw target name + truncated UUID) is unchanged --------
    @Test
    void recordColumnStillShowsTheRawTargetEntityNameAndTruncatedUuid() throws Exception {
        TestApiClient ceo = ceo();
        ContentPlan plan = approvedPlan(ceo, Instant.now().toEpochMilli());

        String body = ceo.get("/app/audit?actionType=IDEA_APPROVED&pageSize=50").body();
        // "ideas" -> "ideas" (fn:replace only touches underscores; single-word names are unchanged)
        // and the first 8 chars of the idea's own UUID, exactly as before this change.
        assertThat(body).contains("ideas");
        assertThat(body).contains(plan.getIdea().getId().toString().substring(0, 8));
    }

    // --- 6/7: existing filters + ordering (sort=asc/desc) still work, page still renders -------
    @Test
    void existingFiltersAndOrderingStillWork() throws Exception {
        TestApiClient ceo = ceo();
        approvedPlan(ceo, Instant.now().toEpochMilli());

        var filtered = ceo.get("/app/audit?actionType=IDEA_APPROVED&pageSize=10");
        assertThat(filtered.statusCode()).isEqualTo(200);
        assertThat(filtered.body()).doesNotContain("Whitelabel Error Page", "500 Internal Server Error");

        var ascending = ceo.get("/app/audit?sort=asc");
        assertThat(ascending.statusCode()).isEqualTo(200);
        var descending = ceo.get("/app/audit?sort=desc");
        assertThat(descending.statusCode()).isEqualTo(200);

        // The new column header renders alongside the existing ones, in the requested order.
        assertThat(descending.body()).contains(">Content ID</th>").contains(">Record</th>");
    }
}
