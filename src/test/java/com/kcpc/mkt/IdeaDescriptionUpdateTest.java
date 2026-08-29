package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.domain.IdeaDescriptionCorrection;
import com.kcpc.mkt.idea.repository.IdeaDescriptionCorrectionRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CEO/Marketing Manager may edit an already-submitted Idea's Description/Details from the note-
 * icon modal (idea-detail.jsp and Reviews -> Ideas -> selected idea both share
 * fragments/idea-description-modal.jspf) - all other users stay read-only, even an Employee
 * holding a PERM_01_IDEA_REVIEW grant (this is a stricter, native-authority-ONLY rule than every
 * other Idea Review action, which grants can also satisfy - see AuthorizationService
 * #requireNativeAuthority vs #requireAuthority). The originally submitted text is never lost: the
 * prior/new pair, the reason, and who/when are preserved in an append-only
 * idea_description_corrections row (IdeaService#updateDescription) before the Idea's own field is
 * overwritten. A reason is mandatory.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdeaDescriptionUpdateTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    IdeaDescriptionCorrectionRepository descriptionCorrectionRepository;

    private static final String MARKETING_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000002";
    private static final String HR_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000003"; // EMPLOYEE access class

    @Test
    void ceoCanUpdateDescriptionAndOriginalIsPreservedInTheCorrectionLedger() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        JsonNode ceoLogin = ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String ceoUserId = ceoLogin.get("userId").asText();

        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"Desc Update CEO " + unique + "\",\"notesRemarks\":\"Original text " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        HttpResponse<String> response = ceo.postForm("/app/ideas/" + ideaId + "/description", Map.of(
                "description", "Updated text " + unique,
                "correctionReason", "Corrected a factual error"));
        assertThat(response.statusCode()).isEqualTo(302); // non-AJAX form POST, plain redirect

        Idea updated = ideaRepository.findById(java.util.UUID.fromString(ideaId)).orElseThrow();
        assertThat(updated.getNotesRemarks()).isEqualTo("Updated text " + unique);

        // Original submitted text is never lost - preserved in the append-only correction ledger
        // alongside the new text, the reason, and who made the change, not just overwritten.
        IdeaDescriptionCorrection correction = descriptionCorrectionRepository.findByIdeaOrderByCorrectedAtDesc(updated)
                .stream().findFirst().orElseThrow();
        assertThat(correction.getPriorDescription()).isEqualTo("Original text " + unique);
        assertThat(correction.getNewDescription()).isEqualTo("Updated text " + unique);
        assertThat(correction.getCorrectionReason()).isEqualTo("Corrected a factual error");
        assertThat(correction.getCorrectedBy().getId()).isEqualTo(java.util.UUID.fromString(ceoUserId));
    }

    @Test
    void marketingManagerCanUpdateDescriptionToo() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String mmEmail = "desc-mm-" + unique + "@kcpcbandhani.local";
        createUser(ceo, "Desc Test MM", mmEmail, MARKETING_MANAGER_ROLE_ID);
        TestApiClient mm = new TestApiClient(port);
        mm.login(mmEmail, "Passw0rd!");

        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"Desc Update MM " + unique + "\",\"notesRemarks\":\"Original MM text " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        HttpResponse<String> response = mm.postFormAjax("/app/ideas/" + ideaId + "/description", Map.of(
                "description", "MM updated text " + unique,
                "correctionReason", "MM correction"));
        assertThat(response.statusCode()).isEqualTo(200);

        Idea updated = ideaRepository.findById(java.util.UUID.fromString(ideaId)).orElseThrow();
        assertThat(updated.getNotesRemarks()).isEqualTo("MM updated text " + unique);
    }

    @Test
    void employeeWithReviewGrantCannotUpdateDescription() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String hrEmail = "desc-hr-grant-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(ceo, "Desc Test HR Grant", hrEmail, HR_MANAGER_ROLE_ID);
        // Even with an active PERM_01_IDEA_REVIEW grant (which DOES let this same employee decide
        // Idea Review Approve/Reject/Retain), description editing stays CEO/MM-only - a stricter
        // rule than every other Idea Review action.
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + hrId + "\",\"permission\":\"PERM_01_IDEA_REVIEW\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"description update permission test\"}");
        TestApiClient hr = new TestApiClient(port);
        hr.login(hrEmail, "Passw0rd!");

        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"Desc Update Denied " + unique + "\",\"notesRemarks\":\"Untouchable text " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        HttpResponse<String> response = hr.postFormAjax("/app/ideas/" + ideaId + "/description", Map.of(
                "description", "Should not be allowed",
                "correctionReason", "attempting anyway"));
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("PERM_ACCESS_CLASS_DENIED");

        Idea unchanged = ideaRepository.findById(java.util.UUID.fromString(ideaId)).orElseThrow();
        assertThat(unchanged.getNotesRemarks()).isEqualTo("Untouchable text " + unique);
    }

    @Test
    void blankReasonIsRejected() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"Desc Update No Reason " + unique + "\",\"notesRemarks\":\"Original " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        Map<String, String> params = new HashMap<>();
        params.put("description", "New text");
        params.put("correctionReason", "");
        HttpResponse<String> response = ceo.postFormAjax("/app/ideas/" + ideaId + "/description", params);
        assertThat(response.statusCode()).isEqualTo(400);

        Idea unchanged = ideaRepository.findById(java.util.UUID.fromString(ideaId)).orElseThrow();
        assertThat(unchanged.getNotesRemarks()).isEqualTo("Original " + unique);
    }

    @Test
    void editControlIsVisibleForCeoButHiddenForAnEmployeeWithViewOnlyAccess() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"Desc Edit Icon " + unique + "\",\"notesRemarks\":\"Visible text " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        String ceoBody = ceo.get("/app/ideas/" + ideaId).body();
        assertThat(ceoBody).contains("id=\"scriptDescriptionEditToggle\"").contains("id=\"scriptDescriptionEditForm\"");

        // An idea's own submitter, an EMPLOYEE, can view their own idea's detail page but must
        // never see the Edit control - CEO/MM only, regardless of who submitted it.
        String hrEmail = "desc-icon-hr-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(ceo, "Desc Icon HR", hrEmail, HR_MANAGER_ROLE_ID);
        TestApiClient hr = new TestApiClient(port);
        hr.login(hrEmail, "Passw0rd!");
        JsonNode ownIdea = hr.postJson("/api/v1/ideas",
                "{\"title\":\"Desc Edit Icon Own " + unique + "\",\"notesRemarks\":\"Own text " + unique + "\"}");
        String ownIdeaId = ownIdea.get("ideaId").asText();

        String hrBody = hr.get("/app/ideas/" + ownIdeaId).body();
        assertThat(hrBody).contains("id=\"scriptDescriptionOpen\""); // can still view
        assertThat(hrBody).doesNotContain("id=\"scriptDescriptionEditToggle\"")
                .doesNotContain("id=\"scriptDescriptionEditForm\""); // but never edit
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"description update test fixture\"}");
        return response.get("userId").asText();
    }
}
