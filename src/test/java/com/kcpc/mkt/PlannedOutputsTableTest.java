package com.kcpc.mkt;

import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputPublicationTargetMappingRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Planned Outputs table redesign: Output | Type | Publication Targets | Action, with the
 * Publication Targets column showing one row per Platform ("Platform : Channel Channel") -
 * channels as chips on the same line as their Platform, no repeated Platform name, no "(count)"
 * clutter - plus per-target Remove, per-group Edit and per-group Remove.
 *
 * A single PlannedOutput can only carry one Reel Type (ERD-CON-008), so selecting multiple Reel
 * Types on "+ Add Output" creates one separate PlannedOutput per selected type instead of one
 * output holding both - but all outputs from one such submission share a reelGroupId, render as
 * ONE grouped row, and always share exactly one Publication Target set (ERD-TBL-011): mapping or
 * unmapping a target through the group's row applies to every member, and per-Reel-Type target
 * overrides are impossible. Non-REEL outputs are simply a "group of one".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlannedOutputsTableTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;
    @Autowired
    PlannedOutputPublicationTargetMappingRepository mappingRepository;

    private static final String TARGET_INSTAGRAM_KCPC = "01926e3e-000a-7000-8000-000000000001";
    private static final String TARGET_YOUTUBE_KCPC = "01926e3e-000a-7000-8000-000000000002";
    private static final String TARGET_FACEBOOK_KCPC = "01926e3e-000a-7000-8000-000000000003";

    @Test
    void plannedOutputsTableShowsOneRowPerPlatformWithChannelChips() throws Exception {
        ContentPlan plan = approvedPlan("Chips Grouping");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        assertThat(ceo.postForm(base + "/outputs",
                Map.of("outputType", "REEL", "reelTypes", "SHORT", "titleDescription", "Product Reel"))
                .statusCode()).isEqualTo(302);
        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        UUID groupId = output.getReelGroupId();

        assertThat(ceo.postForm(base + "/outputs/" + groupId + "/targets",
                Map.of("publicationTargetIds", TARGET_INSTAGRAM_KCPC)).statusCode()).isEqualTo(302);
        assertThat(ceo.postForm(base + "/outputs/" + groupId + "/targets",
                Map.of("publicationTargetIds", TARGET_YOUTUBE_KCPC)).statusCode()).isEqualTo(302);
        assertThat(ceo.postForm(base + "/outputs/" + groupId + "/targets",
                Map.of("publicationTargetIds", TARGET_FACEBOOK_KCPC)).statusCode()).isEqualTo(302);

        HttpResponse<String> page = ceo.get(base);
        String body = page.body();
        assertThat(body).contains("Product Reel");
        assertThat(body).contains("reeltype-chip\">SHORT");
        assertThat(body).contains("target-platform\">Instagram");
        assertThat(body).contains("target-platform\">YouTube");
        assertThat(body).contains("target-platform\">Facebook");
        assertThat(body).contains("class=\"channel-chip\"").contains(">kcpcbandhani");
        // No redundant "(1)" counts or Platform/Channel columns - one row per Platform instead.
        assertThat(body).doesNotContain("Instagram (1)", "target-group-label", "targets-summary");
    }

    @Test
    void selectingBothReelTypesOnAddOutputCreatesTwoSeparatePlannedOutputsSharingOneGroup() throws Exception {
        ContentPlan plan = approvedPlan("Multi Reel Type");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        HttpResponse<String> response = ceo.postFormMulti(base + "/outputs", Map.of(
                "outputType", List.of("REEL"),
                "reelTypes", List.of("SHORT", "LONG"),
                "titleDescription", List.of("Product Reel")));
        assertThat(response.statusCode()).isEqualTo(302);

        List<PlannedOutput> outputs = plannedOutputRepository.findByContentPlan(plan);
        assertThat(outputs).hasSize(2); // one PlannedOutput per Reel Type, never one output holding both
        assertThat(outputs).allMatch(o -> "Product Reel".equals(o.getTitleDescription()));
        assertThat(outputs).extracting(o -> o.getReelType().name())
                .containsExactlyInAnyOrder("SHORT", "LONG");
        // All members of one "+ Add Output" submission share a single reelGroupId (ERD-TBL-011).
        assertThat(outputs).extracting(PlannedOutput::getReelGroupId)
                .containsOnly(outputs.get(0).getReelGroupId());

        HttpResponse<String> page = ceo.get(base);
        assertThat(page.body()).contains("2 planned outputs added.");
    }

    @Test
    void addOutputRejectsReelWithNoReelTypeSelected() throws Exception {
        ContentPlan plan = approvedPlan("Reel No Type");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        HttpResponse<String> response = ceo.postForm(base + "/outputs", Map.of("outputType", "REEL"));
        assertThat(response.statusCode()).isEqualTo(302);

        assertThat(plannedOutputRepository.findByContentPlan(plan)).isEmpty();
        HttpResponse<String> page = ceo.get(base);
        assertThat(page.body()).contains("Select at least one Reel Type when Output Type is Reel");
    }

    @Test
    void mappingTargetOnGroupAppliesToEveryReelTypeMember() throws Exception {
        ContentPlan plan = approvedPlan("Group Target Propagation");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        assertThat(ceo.postFormMulti(base + "/outputs", Map.of(
                "outputType", List.of("REEL"),
                "reelTypes", List.of("SHORT", "LONG"),
                "titleDescription", List.of("Product Reel"))).statusCode()).isEqualTo(302);
        List<PlannedOutput> members = plannedOutputRepository.findByContentPlan(plan);
        UUID groupId = members.get(0).getReelGroupId();

        assertThat(ceo.postForm(base + "/outputs/" + groupId + "/targets",
                Map.of("publicationTargetIds", TARGET_INSTAGRAM_KCPC)).statusCode()).isEqualTo(302);

        // Both the SHORT and the LONG member independently received the same mapping - no
        // per-Reel-Type override is possible.
        for (PlannedOutput member : members) {
            assertThat(mappingRepository.findByPlannedOutput(member))
                    .extracting(m -> m.getPublicationTarget().getId().toString())
                    .containsExactly(TARGET_INSTAGRAM_KCPC);
        }
    }

    @Test
    void removingTargetFromGroupRemovesItFromEveryMember() throws Exception {
        ContentPlan plan = approvedPlan("Group Target Removal");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        assertThat(ceo.postFormMulti(base + "/outputs", Map.of(
                "outputType", List.of("REEL"),
                "reelTypes", List.of("SHORT", "LONG"),
                "titleDescription", List.of("Product Reel"))).statusCode()).isEqualTo(302);
        List<PlannedOutput> members = plannedOutputRepository.findByContentPlan(plan);
        UUID groupId = members.get(0).getReelGroupId();

        assertThat(ceo.postForm(base + "/outputs/" + groupId + "/targets",
                Map.of("publicationTargetIds", TARGET_INSTAGRAM_KCPC)).statusCode()).isEqualTo(302);
        assertThat(ceo.postForm(base + "/outputs/" + groupId + "/targets",
                Map.of("publicationTargetIds", TARGET_YOUTUBE_KCPC)).statusCode()).isEqualTo(302);

        HttpResponse<String> response = ceo.postForm(
                base + "/outputs/" + groupId + "/targets/" + TARGET_INSTAGRAM_KCPC + "/remove", Map.of());
        assertThat(response.statusCode()).isEqualTo(302);

        for (PlannedOutput member : members) {
            var remaining = mappingRepository.findByPlannedOutput(member);
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).getPublicationTarget().getId().toString()).isEqualTo(TARGET_YOUTUBE_KCPC);
        }
    }

    @Test
    void editActionCanGrowGroupAndNewMemberInheritsSharedTargets() throws Exception {
        ContentPlan plan = approvedPlan("Edit Group Add Type");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        assertThat(ceo.postForm(base + "/outputs",
                Map.of("outputType", "REEL", "reelTypes", "SHORT", "titleDescription", "Product Reel"))
                .statusCode()).isEqualTo(302);
        PlannedOutput original = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        UUID groupId = original.getReelGroupId();
        assertThat(ceo.postForm(base + "/outputs/" + groupId + "/targets",
                Map.of("publicationTargetIds", TARGET_INSTAGRAM_KCPC)).statusCode()).isEqualTo(302);

        HttpResponse<String> response = ceo.postFormMulti(base + "/outputs/" + groupId + "/edit", Map.of(
                "outputType", List.of("REEL"),
                "reelTypes", List.of("SHORT", "LONG"),
                "titleDescription", List.of("Renamed Reel")));
        assertThat(response.statusCode()).isEqualTo(302);

        List<PlannedOutput> members = plannedOutputRepository.findByReelGroupId(groupId);
        assertThat(members).hasSize(2);
        assertThat(members).allMatch(m -> "Renamed Reel".equals(m.getTitleDescription()));
        assertThat(members).extracting(m -> m.getReelType().name())
                .containsExactlyInAnyOrder("SHORT", "LONG");
        // The newly added LONG member inherited the group's existing shared target immediately.
        for (PlannedOutput member : members) {
            assertThat(mappingRepository.findByPlannedOutput(member))
                    .extracting(m -> m.getPublicationTarget().getId().toString())
                    .containsExactly(TARGET_INSTAGRAM_KCPC);
        }
    }

    @Test
    void editActionCanShrinkGroupRemovingTheDroppedTypesMappingsToo() throws Exception {
        ContentPlan plan = approvedPlan("Edit Group Drop Type");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        assertThat(ceo.postFormMulti(base + "/outputs", Map.of(
                "outputType", List.of("REEL"),
                "reelTypes", List.of("SHORT", "LONG"),
                "titleDescription", List.of("Product Reel"))).statusCode()).isEqualTo(302);
        List<PlannedOutput> members = plannedOutputRepository.findByContentPlan(plan);
        UUID groupId = members.get(0).getReelGroupId();
        assertThat(ceo.postForm(base + "/outputs/" + groupId + "/targets",
                Map.of("publicationTargetIds", TARGET_INSTAGRAM_KCPC)).statusCode()).isEqualTo(302);
        PlannedOutput longMember = members.stream()
                .filter(m -> m.getReelType().name().equals("LONG")).findFirst().orElseThrow();

        HttpResponse<String> response = ceo.postForm(base + "/outputs/" + groupId + "/edit",
                Map.of("outputType", "REEL", "reelTypes", "SHORT", "titleDescription", "Product Reel"));
        assertThat(response.statusCode()).isEqualTo(302);

        List<PlannedOutput> remaining = plannedOutputRepository.findByReelGroupId(groupId);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getReelType().name()).isEqualTo("SHORT");
        assertThat(plannedOutputRepository.findById(longMember.getId())).isEmpty();
        assertThat(mappingRepository.findByPlannedOutput(longMember)).isEmpty();
    }

    @Test
    void editActionOnNonReelGroupOfOneStillWorksInPlace() throws Exception {
        ContentPlan plan = approvedPlan("Edit Non Reel");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        assertThat(ceo.postForm(base + "/outputs", Map.of("outputType", "PHOTOGRAPHY")).statusCode()).isEqualTo(302);
        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        UUID groupId = output.getReelGroupId();

        HttpResponse<String> response = ceo.postForm(base + "/outputs/" + groupId + "/edit",
                Map.of("outputType", "VIDEO", "titleDescription", "Renamed Output"));
        assertThat(response.statusCode()).isEqualTo(302);

        List<PlannedOutput> members = plannedOutputRepository.findByReelGroupId(groupId);
        assertThat(members).hasSize(1);
        assertThat(members.get(0).getId()).isEqualTo(output.getId()); // same row, edited in place
        assertThat(members.get(0).getOutputType().name()).isEqualTo("VIDEO");
        assertThat(members.get(0).getTitleDescription()).isEqualTo("Renamed Output");
    }

    @Test
    void removeActionDeletesEveryMemberOfTheGroupAndTheirTargetMappings() throws Exception {
        ContentPlan plan = approvedPlan("Remove Group");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        assertThat(ceo.postFormMulti(base + "/outputs", Map.of(
                "outputType", List.of("REEL"),
                "reelTypes", List.of("SHORT", "LONG"),
                "titleDescription", List.of("Product Reel"))).statusCode()).isEqualTo(302);
        List<PlannedOutput> members = plannedOutputRepository.findByContentPlan(plan);
        UUID groupId = members.get(0).getReelGroupId();
        assertThat(ceo.postForm(base + "/outputs/" + groupId + "/targets",
                Map.of("publicationTargetIds", TARGET_INSTAGRAM_KCPC)).statusCode()).isEqualTo(302);

        HttpResponse<String> response = ceo.postForm(base + "/outputs/" + groupId + "/remove", Map.of());
        assertThat(response.statusCode()).isEqualTo(302);

        assertThat(plannedOutputRepository.findByReelGroupId(groupId)).isEmpty();
        for (PlannedOutput member : members) {
            assertThat(plannedOutputRepository.findById(member.getId())).isEmpty();
            assertThat(mappingRepository.findByPlannedOutput(member)).isEmpty();
        }
    }

    @Test
    void addTargetViaAjaxHeaderReturns200InsteadOfRedirectingAndStillMaps() throws Exception {
        ContentPlan plan = approvedPlan("Ajax Add Target");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        assertThat(ceo.postForm(base + "/outputs", Map.of("outputType", "PHOTOGRAPHY")).statusCode()).isEqualTo(302);
        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        UUID groupId = output.getReelGroupId();

        HttpResponse<String> response = ceo.postFormAjax(base + "/outputs/" + groupId + "/targets",
                Map.of("publicationTargetIds", TARGET_INSTAGRAM_KCPC));
        assertThat(response.statusCode()).isEqualTo(200);

        assertThat(mappingRepository.findByPlannedOutput(output))
                .extracting(m -> m.getPublicationTarget().getId().toString())
                .containsExactly(TARGET_INSTAGRAM_KCPC);
    }

    @Test
    void removeTargetViaAjaxHeaderReturns200InsteadOfRedirectingAndStillUnmaps() throws Exception {
        ContentPlan plan = approvedPlan("Ajax Remove Target");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        assertThat(ceo.postForm(base + "/outputs", Map.of("outputType", "PHOTOGRAPHY")).statusCode()).isEqualTo(302);
        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        UUID groupId = output.getReelGroupId();
        assertThat(ceo.postForm(base + "/outputs/" + groupId + "/targets",
                Map.of("publicationTargetIds", TARGET_INSTAGRAM_KCPC)).statusCode()).isEqualTo(302);

        HttpResponse<String> response = ceo.postFormAjax(
                base + "/outputs/" + groupId + "/targets/" + TARGET_INSTAGRAM_KCPC + "/remove", Map.of());
        assertThat(response.statusCode()).isEqualTo(200);

        assertThat(mappingRepository.findByPlannedOutput(output)).isEmpty();
    }

    @Test
    void ajaxRequestOnDomainFailureReturnsJsonErrorInsteadOfRedirect() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        UUID bogusGroupId = UUID.randomUUID();

        HttpResponse<String> response = ceo.postFormAjax(
                "/app/deliverables/" + UUID.randomUUID() + "/outputs/" + bogusGroupId + "/targets",
                Map.of("publicationTargetIds", TARGET_INSTAGRAM_KCPC));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("RESOURCE_NOT_FOUND");
    }

    @Test
    void noJsFallbackStillRedirectsWhenAjaxHeaderIsAbsent() throws Exception {
        ContentPlan plan = approvedPlan("No JS Fallback");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        assertThat(ceo.postForm(base + "/outputs", Map.of("outputType", "PHOTOGRAPHY")).statusCode()).isEqualTo(302);
        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        UUID groupId = output.getReelGroupId();

        HttpResponse<String> response = ceo.postForm(base + "/outputs/" + groupId + "/targets",
                Map.of("publicationTargetIds", TARGET_INSTAGRAM_KCPC));
        assertThat(response.statusCode()).isEqualTo(302);
    }

    @Test
    void addOutputViaAjaxHeaderReturns200WithGroupJsonInsteadOfRedirecting() throws Exception {
        ContentPlan plan = approvedPlan("Ajax Add Output");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        HttpResponse<String> response = ceo.postFormAjax(base + "/outputs",
                Map.of("outputType", "PHOTOGRAPHY", "titleDescription", "Ajax created output"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"outputType\":\"PHOTOGRAPHY\"", "Ajax created output");

        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        assertThat(response.body()).contains(output.getReelGroupId().toString());
    }

    @Test
    void editOutputViaAjaxHeaderReturns200WithUpdatedGroupJsonAndStillUpdates() throws Exception {
        ContentPlan plan = approvedPlan("Ajax Edit Output");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        assertThat(ceo.postForm(base + "/outputs", Map.of("outputType", "PHOTOGRAPHY")).statusCode()).isEqualTo(302);
        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        UUID groupId = output.getReelGroupId();

        HttpResponse<String> response = ceo.postFormAjax(base + "/outputs/" + groupId + "/edit",
                Map.of("outputType", "PHOTOGRAPHY", "titleDescription", "Renamed via AJAX"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Renamed via AJAX");

        assertThat(plannedOutputRepository.findByReelGroupId(groupId).get(0).getTitleDescription())
                .isEqualTo("Renamed via AJAX");
    }

    @Test
    void removeOutputViaAjaxHeaderReturns200InsteadOfRedirectingAndStillRemoves() throws Exception {
        ContentPlan plan = approvedPlan("Ajax Remove Output");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        assertThat(ceo.postForm(base + "/outputs", Map.of("outputType", "PHOTOGRAPHY")).statusCode()).isEqualTo(302);
        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        UUID groupId = output.getReelGroupId();

        HttpResponse<String> response = ceo.postFormAjax(base + "/outputs/" + groupId + "/remove", Map.of());
        assertThat(response.statusCode()).isEqualTo(200);

        assertThat(plannedOutputRepository.findByReelGroupId(groupId)).isEmpty();
    }

    private ContentPlan approvedPlan(String title) throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String ideaTitle = title + " " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", ideaTitle)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(ideaTitle)).findFirst().orElseThrow();
        assertThat(ceo.postForm("/app/ideas/" + idea.getId() + "/review",
                Map.of("decision", "APPROVE", "cameramanMark", "1.0", "editorMark", "1.0")).statusCode()).isEqualTo(302);
        return contentPlanRepository.findByIdea(idea).orElseThrow();
    }
}
