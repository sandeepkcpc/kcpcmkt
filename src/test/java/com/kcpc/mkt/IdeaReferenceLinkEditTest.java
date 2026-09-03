package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CEO/Marketing Manager may edit an already-submitted Idea's Reference Link inline from the Idea
 * Review screen (idea-detail.jsp and Reviews -> Ideas -> selected idea both share
 * fragments/idea-reference-link-edit.jspf) - all other users stay read-only, even an Employee
 * holding a PERM_01_IDEA_REVIEW grant, mirroring the same native-authority-ONLY rule already
 * enforced for Description edits (IdeaDescriptionUpdateTest). Same Idea record, same Idea ID -
 * never a new Idea/version, and no other field is touched. The new value must be a valid http(s)
 * URL (IdeaService#isValidUrl). "Last Updated" (IdeaMvcController#computeLastUpdated) reflects the
 * edit alongside the lifecycle history.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdeaReferenceLinkEditTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;

    private static final String MARKETING_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000002";
    private static final String HR_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000003"; // EMPLOYEE access class

    @Test
    void ceoCanEditAndSaveReferenceLink() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"RefLink CEO " + unique + "\",\"referenceLink\":\"https://old.example.com/" + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        HttpResponse<String> response = ceo.postForm("/app/ideas/" + ideaId + "/reference-link", Map.of(
                "referenceLink", "https://new.example.com/" + unique));
        assertThat(response.statusCode()).isEqualTo(302); // non-AJAX form POST, plain redirect

        Idea updated = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        assertThat(updated.getReferenceLink()).isEqualTo("https://new.example.com/" + unique);
    }

    @Test
    void marketingManagerCanEditAndSaveReferenceLinkToo() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String mmEmail = "reflink-mm-" + unique + "@kcpcbandhani.local";
        createUser(ceo, "RefLink Test MM", mmEmail, MARKETING_MANAGER_ROLE_ID);
        TestApiClient mm = new TestApiClient(port);
        mm.login(mmEmail, "Passw0rd!");

        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"RefLink MM " + unique + "\",\"referenceLink\":\"https://old-mm.example.com/" + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        HttpResponse<String> response = mm.postFormAjax("/app/ideas/" + ideaId + "/reference-link", Map.of(
                "referenceLink", "https://new-mm.example.com/" + unique));
        assertThat(response.statusCode()).isEqualTo(200);

        Idea updated = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        assertThat(updated.getReferenceLink()).isEqualTo("https://new-mm.example.com/" + unique);
    }

    @Test
    void employeeWithReviewGrantCannotEditReferenceLink() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String hrEmail = "reflink-hr-grant-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(ceo, "RefLink Test HR Grant", hrEmail, HR_MANAGER_ROLE_ID);
        // Even with an active PERM_01_IDEA_REVIEW grant (which DOES let this same employee decide
        // Idea Review Approve/Reject/Retain), Reference Link editing stays CEO/MM-only.
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + hrId + "\",\"permission\":\"PERM_01_IDEA_REVIEW\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"reference link permission test\"}");
        TestApiClient hr = new TestApiClient(port);
        hr.login(hrEmail, "Passw0rd!");

        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"RefLink Denied " + unique + "\",\"referenceLink\":\"https://untouchable.example.com/" + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        HttpResponse<String> response = hr.postFormAjax("/app/ideas/" + ideaId + "/reference-link", Map.of(
                "referenceLink", "https://should-not-be-allowed.example.com"));
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("PERM_ACCESS_CLASS_DENIED");

        Idea unchanged = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        assertThat(unchanged.getReferenceLink()).isEqualTo("https://untouchable.example.com/" + unique);

        // The edit control itself must never render for a non-CEO/MM viewer either.
        String hrBody = hr.get("/app/ideas/" + ideaId).body();
        assertThat(hrBody).doesNotContain("id=\"refLinkEditToggle\"").doesNotContain("id=\"refLinkEditForm\"");
    }

    @Test
    void invalidUrlIsRejected() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"RefLink Bad URL " + unique + "\",\"referenceLink\":\"https://original.example.com/" + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        Map<String, String> params = new HashMap<>();
        params.put("referenceLink", "not-a-valid-url");
        HttpResponse<String> response = ceo.postFormAjax("/app/ideas/" + ideaId + "/reference-link", params);
        assertThat(response.statusCode()).isEqualTo(400);

        Idea unchanged = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        assertThat(unchanged.getReferenceLink()).isEqualTo("https://original.example.com/" + unique);
    }

    @Test
    void lastUpdatedChangesAfterASuccessfulReferenceLinkUpdate() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"RefLink Last Updated " + unique + "\",\"referenceLink\":\"https://before.example.com/" + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        String beforeBody = ceo.get("/app/ideas/" + ideaId).body();
        String beforeLastUpdated = extractLastUpdated(beforeBody);

        Thread.sleep(1100); // ensure a strictly-later timestamp than the sub-second submit event
        HttpResponse<String> response = ceo.postForm("/app/ideas/" + ideaId + "/reference-link", Map.of(
                "referenceLink", "https://after.example.com/" + unique));
        assertThat(response.statusCode()).isEqualTo(302);

        String afterBody = ceo.get("/app/ideas/" + ideaId).body();
        String afterLastUpdated = extractLastUpdated(afterBody);
        assertThat(afterLastUpdated).isNotEqualTo(beforeLastUpdated);
    }

    @Test
    void updatedReferenceLinkPersistsAfterPageRefresh() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"RefLink Refresh " + unique + "\",\"referenceLink\":\"https://stale.example.com/" + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        HttpResponse<String> response = ceo.postForm("/app/ideas/" + ideaId + "/reference-link", Map.of(
                "referenceLink", "https://fresh.example.com/" + unique));
        assertThat(response.statusCode()).isEqualTo(302);

        // A fresh GET (simulating a page refresh), not the redirect response itself.
        String refreshedBody = ceo.get("/app/ideas/" + ideaId).body();
        assertThat(refreshedBody).contains("https://fresh.example.com/" + unique);
        assertThat(refreshedBody).doesNotContain("https://stale.example.com/" + unique);

        Idea persisted = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        assertThat(persisted.getReferenceLink()).isEqualTo("https://fresh.example.com/" + unique);
    }

    /** idea-detail.jsp renders "Last Updated" via kcpc:ist(idtLastUpdated) inside the timeline
     * section - grabbing the raw rendered timestamp text is enough to detect a change, without
     * needing to parse the IST-formatted string. */
    private String extractLastUpdated(String body) {
        int marker = body.indexOf("Last Updated");
        assertThat(marker).isGreaterThan(-1);
        int snippetEnd = Math.min(body.length(), marker + 400);
        return body.substring(marker, snippetEnd);
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"reference link update test fixture\"}");
        return response.get("userId").asText();
    }
}
