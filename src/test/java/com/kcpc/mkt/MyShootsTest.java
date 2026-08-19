package com.kcpc.mkt;

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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-067: "My Shoots" (Model employee screen) - a Model sees only the shoots they're linked to
 * via {@code ContentPlanTalentEntry.talentUser} (never another user's), split into Upcoming/Past
 * by the physical planned shoot date, with the raw WorkflowStatus name shown (not a friendly
 * per-stage relabel, since a Model isn't executing a specific stage) and other co-talent names
 * listed under "Other Talent".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyShootsTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";

    @Test
    void modelSeesOnlyOwnLinkedShootsSplitIntoUpcomingAndPast() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String model1Email = "ms-model1-" + unique + "@kcpcbandhani.local";
        String model1 = createUser(ceo, "Aisha Sharma " + unique, model1Email, MODEL_ROLE_ID);
        String model2 = createUser(ceo, "Neha Kapoor " + unique, "ms-model2-" + unique + "@kcpcbandhani.local", MODEL_ROLE_ID);
        String model3Email = "ms-model3-" + unique + "@kcpcbandhani.local";
        String model3 = createUser(ceo, "Riya Verma " + unique, model3Email, MODEL_ROLE_ID);

        // Upcoming plan: model1 + model2, future planned shoot date (standard schedule).
        ContentPlan upcomingPlan = createApprovedPlan(ceo, "My Shoots Upcoming Idea " + unique);
        String upcomingBase = "/app/deliverables/" + upcomingPlan.getId();
        assertRedirect(ceo.postFormMulti(upcomingBase + "/parameters", Map.of(
                "contentPriority", java.util.List.of("MEDIUM"),
                "modelUserIds", java.util.List.of(model1, model2))));
        String futureLiveDate = LocalDate.now().plusDays(20).toString();
        assertRedirect(ceo.postForm(upcomingBase + "/schedule/standard", Map.of("plannedLiveDate", futureLiveDate)));

        // Past plan: model1 only, explicit past shoot date (urgent schedule).
        ContentPlan pastPlan = createApprovedPlan(ceo, "My Shoots Past Idea " + unique);
        String pastBase = "/app/deliverables/" + pastPlan.getId();
        assertRedirect(ceo.postFormMulti(pastBase + "/parameters", Map.of(
                "contentPriority", java.util.List.of("MEDIUM"),
                "modelUserIds", java.util.List.of(model1))));
        String pastShootDate = LocalDate.now().minusDays(5).toString();
        String pastEditDate = LocalDate.now().minusDays(2).toString();
        String futureLiveDate2 = LocalDate.now().plusDays(3).toString();
        assertRedirect(ceo.postForm(pastBase + "/schedule/urgent", Map.of(
                "plannedLiveDate", futureLiveDate2, "shootDate", pastShootDate, "editDate", pastEditDate,
                "urgencyReason", "my shoots test fixture")));

        TestApiClient model1Client = new TestApiClient(port);
        model1Client.login(model1Email, "Passw0rd!");

        HttpResponse<String> nav = model1Client.get("/app/my-shoots");
        assertThat(nav.statusCode()).isEqualTo(200);
        String body = nav.body();

        // Nav shows "My Shoots", not "My Work", for a Model.
        assertThat(body).contains(">My Shoots<");
        assertThat(body).doesNotContain(">My Work<");

        // Upcoming tab: this plan's row, own role literal "Model", co-talent's name, raw status label.
        assertThat(body).contains(upcomingPlan.getContentId());
        assertThat(body).contains("Neha Kapoor " + unique); // other talent on the upcoming plan
        assertThat(body).doesNotContain("Aisha Sharma " + unique + "</td>"); // never lists self as "Other Talent"
        assertThat(body).contains(">Planning<"); // raw WorkflowStatus name - still PL, planning review was never submitted

        // Past tab: this plan's row too - model1 is linked, shoot date is in the past.
        assertThat(body).contains(pastPlan.getContentId());

        // KPI: Upcoming Shoots count reflects only the upcoming-bucketed plan (1), not the past one.
        assertThat(body).contains("Upcoming Shoots");

        // model3 has no linked shoots at all - own-data-only privacy: neither plan appears.
        TestApiClient model3Client = new TestApiClient(port);
        model3Client.login(model3Email, "Passw0rd!");
        HttpResponse<String> model3Page = model3Client.get("/app/my-shoots");
        assertThat(model3Page.statusCode()).isEqualTo(200);
        assertThat(model3Page.body()).doesNotContain(upcomingPlan.getContentId()).doesNotContain(pastPlan.getContentId());
        assertThat(model3Page.body()).contains("No upcoming shoots.").contains("No past shoots.");

        // A non-Model employee still sees "My Work", not "My Shoots".
        String camEmail = "ms-cam-" + unique + "@kcpcbandhani.local";
        createUser(ceo, "My Shoots Cam " + unique, camEmail, CAMERA_PERSON_ROLE_ID);
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");
        HttpResponse<String> camMyWork = camClient.get("/app/my-work");
        assertThat(camMyWork.statusCode()).isEqualTo(200);
        assertThat(camMyWork.body()).contains(">My Work<").doesNotContain(">My Shoots<");
    }

    private ContentPlan createApprovedPlan(TestApiClient ceo, String ideaTitle) throws Exception {
        assertThat(ceo.postForm("/app/ideas", Map.of("title", ideaTitle)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(ideaTitle)).findFirst().orElseThrow();
        assertRedirect(ceo.postForm("/app/ideas/" + idea.getId() + "/review",
                Map.of("decision", "APPROVE", "cameramanMark", "1.0", "editorMark", "1.0")));
        return contentPlanRepository.findByIdea(idea).orElseThrow();
    }

    private void assertRedirect(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(302);
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        var response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"my shoots test fixture\"}");
        return response.get("userId").asText();
    }
}
