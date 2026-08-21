package com.kcpc.mkt;

import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.masterdata.domain.CompanyChannel;
import com.kcpc.mkt.masterdata.repository.CompanyChannelRepository;
import com.kcpc.mkt.masterdata.repository.PublicationTargetRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.publishing.domain.ActualPublicationEvent;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-076 (defect fix): Content Pipeline's Platforms column - one icon+count chip per planned
 * Platform, ALWAYS a real clickable {@code <button>} opening a popover of every real (Planned
 * Output, Publication Target) task's status - "active" (green) vs "muted" (grey) is a purely
 * cosmetic color cue (any published vs all still pending), never a click-eligibility gate, since
 * the popover is for viewing task status, not only for opening live links. A pure display feature
 * over {@code PipelineDashboardService.buildRows} - never touches the actual publishing workflow,
 * permissions, or evidence records themselves. Drives a real Content Plan end-to-end through
 * Shoot/Edit/Publishing (same fixture shape as {@link CeoPipelineDashboardTest}) since ENG-043
 * means CEO native authority does not bypass the active-assignee execution gates.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PipelinePlatformPopoverTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;
    @Autowired
    ActualPublicationEventRepository actualPublicationEventRepository;
    @Autowired
    CompanyChannelRepository companyChannelRepository;
    @Autowired
    PublicationTargetRepository publicationTargetRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String TARGET_INSTAGRAM_KCPC = "01926e3e-000a-7000-8000-000000000001";
    private static final String TARGET_YOUTUBE_KCPC = "01926e3e-000a-7000-8000-000000000002";
    private static final String INSTAGRAM_PLATFORM_ID = "01926e3e-0008-7000-8000-000000000001";

    @Test
    void platformChipShowsMutedWhenPlannedOnlyAndActiveWithPopoverWhenPublished() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String camEmail = "ppt-cam-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "Popover Test Cam", camEmail, CAMERA_PERSON_ROLE_ID);
        String edEmail = "ppt-ed-" + unique + "@kcpcbandhani.local";
        String edId = createUser(ceo, "Popover Test Editor", edEmail, VIDEO_EDITOR_ROLE_ID);
        String pubEmail = "ppt-pub-" + unique + "@kcpcbandhani.local";
        String pubId = createUser(ceo, "Popover Test Publisher", pubEmail, PUBLISHER_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"platform popover test publisher grant\"}");

        String ideaTitle = "Platform Popover Test " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", ideaTitle)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(ideaTitle)).findFirst().orElseThrow();
        assertThat(ceo.postForm("/app/ideas/" + idea.getId() + "/review",
                Map.of("decision", "APPROVE", "cameramanMark", "1.0", "editorMark", "1.0")).statusCode())
                .isEqualTo(302);
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        String planId = plan.getId().toString();
        String base = "/app/deliverables/" + planId;
        String skuTag = "ppt-sku-" + unique;

        assertThat(ceo.postFormMulti(base + "/parameters", Map.of(
                "contentPriority", java.util.List.of("MEDIUM"),
                "skuReference", java.util.List.of(skuTag),
                "folderLink", java.util.List.of("https://drive.example.com/ppt-" + unique))).statusCode()).isEqualTo(302);
        assertThat(ceo.postForm(base + "/outputs", Map.of("outputType", "PHOTOGRAPHY")).statusCode()).isEqualTo(302);
        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        assertThat(ceo.postForm(base + "/outputs/" + output.getReelGroupId() + "/targets",
                Map.of("publicationTargetIds", TARGET_INSTAGRAM_KCPC)).statusCode()).isEqualTo(302);
        assertThat(ceo.postForm(base + "/outputs/" + output.getReelGroupId() + "/targets",
                Map.of("publicationTargetIds", TARGET_YOUTUBE_KCPC)).statusCode()).isEqualTo(302);

        assertThat(ceo.postForm(base + "/shooting-assignments", Map.of("cameramanUserId", camId)).statusCode()).isEqualTo(302);
        String plannedLiveDate = LocalDate.now().plusDays(10).toString();
        assertThat(ceo.postForm(base + "/schedule/standard", Map.of("plannedLiveDate", plannedLiveDate)).statusCode())
                .isEqualTo(302);
        assertThat(ceo.postForm(base + "/planning-review/submit", Map.of()).statusCode()).isEqualTo(302);
        assertThat(ceo.postForm(base + "/planning-review/decision", Map.of("approve", "true")).statusCode()).isEqualTo(302);

        // Confirm the workflow actually advanced (a 302 alone doesn't prove success - a validation
        // failure also redirects, just with a flash error message) before driving further.
        assertThat(contentPlanRepository.findById(plan.getId()).orElseThrow()
                .getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("SA");

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        assertThat(cam.postForm(base + "/shooting/start", Map.of()).statusCode()).isEqualTo(302);
        assertThat(cam.postForm(base + "/shooting/review/submit", Map.of()).statusCode()).isEqualTo(302);
        assertThat(ceo.postForm(base + "/shooting/review/decision",
                Map.of("approve", "true", "qualifyingRecipientUserIds", camId)).statusCode()).isEqualTo(302);
        // SAP is a resting status - Editor Assignment (below) is what triggers SAP->EA, the same
        // way Shoot Assignment triggers PLAP->SA.
        assertThat(contentPlanRepository.findById(plan.getId()).orElseThrow()
                .getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("SAP");

        assertThat(ceo.postForm(base + "/editing/assignments", Map.of("editorUserId", edId)).statusCode()).isEqualTo(302);
        assertThat(contentPlanRepository.findById(plan.getId()).orElseThrow()
                .getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("EA");
        TestApiClient editor = new TestApiClient(port);
        editor.login(edEmail, "Passw0rd!");
        assertThat(editor.postForm(base + "/editing/start", Map.of()).statusCode()).isEqualTo(302);
        assertThat(editor.postForm(base + "/editing/review/submit", Map.of()).statusCode()).isEqualTo(302);
        assertThat(ceo.postForm(base + "/editing/review/decision",
                Map.of("approve", "true", "qualifyingRecipientUserIds", edId)).statusCode()).isEqualTo(302);
        assertThat(contentPlanRepository.findById(plan.getId()).orElseThrow()
                .getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("RFP");

        assertThat(ceo.postForm(base + "/publishing-assignments", Map.of("publisherUserId", pubId)).statusCode()).isEqualTo(302);
        TestApiClient publisher = new TestApiClient(port);
        publisher.login(pubEmail, "Passw0rd!");
        assertThat(publisher.postForm(base + "/publishing/start", Map.of()).statusCode()).isEqualTo(302);
        assertThat(contentPlanRepository.findById(plan.getId()).orElseThrow()
                .getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PUBG");

        String originalEvidenceUrl = "https://instagram.com/p/original-" + unique;
        String pastDate = LocalDate.now().minusDays(1).toString();
        assertThat(publisher.postForm(base + "/publishing/events",
                Map.of("plannedOutputId", output.getId().toString(), "publicationTargetId", TARGET_INSTAGRAM_KCPC,
                        "eventType", "ORIGINAL", "actualPublicationTimestamp", pastDate,
                        "evidenceUrl", originalEvidenceUrl)).statusCode()).isEqualTo(302);

        String q = "?q=" + skuTag + "&size=50";
        String pageBody = ceo.get("/app/pipeline" + q).body();

        // Instagram: at least one published channel -> active/clickable chip (icon + count only,
        // no platform-name text in the chip itself - the name is only in aria-label/title/popover)
        // with a popover.
        assertThat(pageBody).contains("pipeline-platform-chip active");
        assertThat(pageBody).contains("aria-label=\"Instagram: 1 of 1 published\"");
        assertThat(pageBody).contains("<span class=\"pipeline-platform-count\">&times;1</span>");
        assertThat(pageBody).contains("Instagram (1)"); // popover header
        assertThat(pageBody).contains("kcpcbandhani").contains("Published").contains("Open &#8599;");
        assertThat(pageBody).contains("href=\"" + originalEvidenceUrl + "\"");

        // YouTube: planned only, nothing published -> muted color, but STILL a real clickable
        // <button> with its own popover (0/1 published) - clickability is never gated on whether
        // anything has been published yet.
        assertThat(pageBody).contains("pipeline-platform-chip muted");
        assertThat(pageBody).contains("aria-label=\"YouTube: 0 of 1 published\"");
        assertThat(pageBody).contains("YouTube (1)"); // popover header
        assertThat(pageBody).contains("0/1 published");
        int youtubeHeaderIdx = pageBody.indexOf("YouTube (1)");
        assertThat(youtubeHeaderIdx).isPositive();
        int youtubeTableEnd = pageBody.indexOf("</table>", youtubeHeaderIdx);
        String youtubePopoverBlock = pageBody.substring(youtubeHeaderIdx, youtubeTableEnd);
        // No Output column anywhere in the popover; the one pending row shows "-" for status/link,
        // never a disabled button, empty cell, "N/A", or mojibake/em-dash.
        assertThat(youtubePopoverBlock).doesNotContain("Open &#8599;").doesNotContain(">Output<")
                .contains(">Pending<").doesNotContain("N/A").doesNotContain("â").doesNotContain("—");
        int youtubeChipIdx = pageBody.indexOf("aria-label=\"YouTube: 0 of 1 published\"");
        int youtubeChipTagStart = pageBody.lastIndexOf("<button", youtubeChipIdx);
        assertThat(youtubeChipTagStart).isPositive();

        // Evidence URL correction: the popover must show the CURRENT effective (corrected) URL,
        // not the original - and the original event row itself is never mutated (a new, separate
        // correction row is appended instead - existing PublishingService behavior, untouched).
        ActualPublicationEvent event = actualPublicationEventRepository.findByContentPlan(plan).stream()
                .filter(e -> e.getPublicationTarget().getId().toString().equals(TARGET_INSTAGRAM_KCPC))
                .findFirst().orElseThrow();
        String correctedUrl = "https://instagram.com/p/corrected-" + unique;
        assertThat(ceo.postJson("/api/v1/publishing/events/" + event.getId() + "/evidence-corrections",
                "{\"correctedEvidenceUrl\":\"" + correctedUrl + "\",\"correctionReason\":\"platform popover test fixture\"}")
                .get("correctedEvidenceUrl").asText()).isEqualTo(correctedUrl);

        String afterCorrection = ceo.get("/app/pipeline" + q).body();
        assertThat(afterCorrection).contains("href=\"" + correctedUrl + "\"");
        assertThat(afterCorrection).doesNotContain("href=\"" + originalEvidenceUrl + "\"");
        // The original event's OWN evidenceUrl is never overwritten - only a correction row is appended.
        assertThat(actualPublicationEventRepository.findById(event.getId()).orElseThrow().getEvidenceUrl())
                .isEqualTo(originalEvidenceUrl);

        // Partial publish: add a second Instagram channel to the same output, left unpublished ->
        // "Instagram ×2" chip, still active (>=1 published), popover shows both rows and the
        // progress text reflects "1/2 published".
        String newChannelHandle = "ppt-channel-" + unique;
        assertThat(ceo.postForm("/app/admin/catalogue/channels",
                Map.of("channelHandle", newChannelHandle, "catalogueReason", "platform popover test fixture")).statusCode())
                .isEqualTo(302);
        CompanyChannel newChannel = companyChannelRepository.findAll().stream()
                .filter(c -> c.getChannelHandle().equals(newChannelHandle)).findFirst().orElseThrow();
        assertThat(ceo.postForm("/app/admin/catalogue/targets",
                Map.of("platformId", INSTAGRAM_PLATFORM_ID, "channelId", newChannel.getId().toString(),
                        "targetName", "Platform Popover Test Target " + unique,
                        "catalogueReason", "platform popover test fixture")).statusCode()).isEqualTo(302);
        var newTarget = publicationTargetRepository.findAll().stream()
                .filter(t -> t.getChannel().getId().equals(newChannel.getId())).findFirst().orElseThrow();
        assertThat(ceo.postForm(base + "/outputs/" + output.getReelGroupId() + "/targets",
                Map.of("publicationTargetIds", newTarget.getId().toString())).statusCode()).isEqualTo(302);

        String withPartial = ceo.get("/app/pipeline" + q).body();
        assertThat(withPartial).contains("<span class=\"pipeline-platform-count\">&times;2</span>");
        assertThat(withPartial).contains("Instagram (2)"); // popover header
        assertThat(withPartial).contains("1/2 published");
        assertThat(withPartial).contains(newChannelHandle);
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        var response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"platform popover test fixture\"}");
        return response.get("userId").asText();
    }
}
