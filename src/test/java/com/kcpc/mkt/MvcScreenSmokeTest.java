package com.kcpc.mkt;

import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.performance.domain.PerformanceObligation;
import com.kcpc.mkt.performance.repository.PerformanceObligationRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the MVC Deliverable Detail shell (UI/UX Design Specification §9.2-9.15) end-to-end
 * through real HTML form POSTs (not the JSON REST API), exactly as a browser would, across every
 * status panel from Idea Submission through Completed. This is a regression guard for the class
 * of MVC-only bug that JUnit-against-REST and `mvn compile` structurally cannot catch - Hibernate
 * `LazyInitializationException` from a JSP reading a LAZY association outside any open session
 * (`open-in-view` is disabled), and null-hostile `Collectors.toMap` usage building a view model -
 * both of which this test found and which are now fixed (see ENG-024 in
 * docs/IMPLEMENTATION_DECISIONS.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MvcScreenSmokeTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;
    @Autowired
    PerformanceObligationRepository obligationRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLICATION_TARGET_ID = "01926e3e-000a-7000-8000-000000000001";

    @Test
    void mvcScreensRenderWithoutErrorAcrossTheGoldenPath() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!"); // primes CSRF + REST login (also valid for MVC).

        String camId = createUser(ceo, "MVC Camera", "mvc-camera-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        String edId = createUser(ceo, "MVC Editor", "mvc-editor-" + unique + "@kcpcbandhani.local", VIDEO_EDITOR_ROLE_ID);

        assertOk(ceo.get("/app/my-work"));
        assertOk(ceo.get("/app/pipeline"));
        assertOk(ceo.get("/app/ideas"));
        assertOk(ceo.get("/app/ideas/new"));

        HttpResponse<String> submit = ceo.postForm("/app/ideas",
                Map.of("title", "MVC Smoke Test " + unique));
        assertThat(submit.statusCode()).isEqualTo(302);

        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals("MVC Smoke Test " + unique)).findFirst().orElseThrow();
        assertOk(ceo.get("/app/ideas/" + idea.getId()));

        HttpResponse<String> approve = ceo.postForm("/app/ideas/" + idea.getId() + "/review",
                Map.of("decision", "APPROVE", "cameramanMark", "1.0", "editorMark", "1.0"));
        assertThat(approve.statusCode()).isEqualTo(302);

        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        UUID planId = plan.getId();
        String base = "/app/deliverables/" + planId;
        assertOk(ceo.get(base)); // PL

        String liveDate = LocalDate.now().plusDays(10).toString();
        assertRedirect(ceo.postForm(base + "/parameters",
                Map.of("contentPriority", "MEDIUM", "folderLink", "https://drive.example.com/mvc-" + unique)));
        assertRedirect(ceo.postForm(base + "/schedule/standard", Map.of("plannedLiveDate", liveDate)));
        assertRedirect(ceo.postForm(base + "/shooting-assignments", Map.of("cameramanUserId", camId)));
        assertRedirect(ceo.postForm(base + "/outputs", Map.of("outputType", "PHOTOGRAPHY")));

        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        assertRedirect(ceo.postForm(base + "/outputs/" + output.getId() + "/targets",
                Map.of("publicationTargetIds", PUBLICATION_TARGET_ID)));

        assertRedirect(ceo.postForm(base + "/planning-review/submit", Map.of()));
        assertOk(ceo.get(base)); // PLRV
        assertRedirect(ceo.postForm(base + "/planning-review/decision", Map.of("approve", "true")));
        assertOk(ceo.get(base)); // SA

        assertRedirect(ceo.postForm(base + "/shooting/start", Map.of()));
        assertOk(ceo.get(base)); // SIP
        assertRedirect(ceo.postForm(base + "/shooting/review/submit", Map.of()));
        assertOk(ceo.get(base)); // SRV
        assertRedirect(ceo.postForm(base + "/shooting/review/decision",
                Map.of("approve", "true", "qualifyingRecipientUserIds", camId)));
        assertOk(ceo.get(base)); // SAP

        assertRedirect(ceo.postForm(base + "/editing/assignments", Map.of("editorUserId", edId)));
        assertOk(ceo.get(base)); // EA
        assertRedirect(ceo.postForm(base + "/editing/start", Map.of()));
        assertOk(ceo.get(base)); // ED
        assertRedirect(ceo.postForm(base + "/editing/review/submit", Map.of()));
        assertOk(ceo.get(base)); // ERV
        assertRedirect(ceo.postForm(base + "/editing/review/decision",
                Map.of("approve", "true", "qualifyingRecipientUserIds", edId)));
        assertOk(ceo.get(base)); // RFP

        assertRedirect(ceo.postForm(base + "/publishing/start", Map.of()));
        assertOk(ceo.get(base)); // PUBG

        String pastDate = LocalDate.now().minusDays(3).toString();
        assertRedirect(ceo.postForm(base + "/publishing/events",
                Map.of("plannedOutputId", output.getId().toString(), "publicationTargetId", PUBLICATION_TARGET_ID,
                        "eventType", "ORIGINAL", "actualPublicationTimestamp", pastDate,
                        "evidenceUrl", "https://instagram.com/p/mvc-" + unique)));
        HttpResponse<String> deliverableAfterEvent = ceo.get(base);
        assertOk(deliverableAfterEvent); // PP - this is exactly where the Collectors.toMap NPE fired.

        List<PerformanceObligation> obligations = obligationRepository.findByEvent_ContentPlan_Id(planId);
        assertThat(obligations).isNotEmpty();
        for (PerformanceObligation obligation : obligations) {
            assertRedirect(ceo.postForm(base + "/performance/" + obligation.getId() + "/draft",
                    Map.of("views3sec", "800", "plays", "1000", "averageWatchTimeSeconds", "12.5",
                            "videoLengthSeconds", "20.0", "linkClicks", "0", "clicksIsNa", "true",
                            "impressions", "5000")));
            assertRedirect(ceo.postForm(base + "/performance/" + obligation.getId() + "/submit", Map.of()));
        }

        HttpResponse<String> finalPage = ceo.get(base);
        assertOk(finalPage); // COMP
        assertThat(finalPage.body()).contains("COMP");
        assertThat(finalPage.body()).doesNotContain("LazyInitializationException", "NullPointerException",
                "Whitelabel Error Page");
    }

    private void assertOk(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("LazyInitializationException", "NullPointerException",
                "Whitelabel Error Page", "500 Internal Server Error");
    }

    private void assertRedirect(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(302);
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        var response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"mvc smoke test fixture\"}");
        return response.get("userId").asText();
    }
}
