package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reel Type is optional (business decision - the V31 redesign already dropped the Reel Type field
 * from the Idea Review "Planned Outputs" grid entirely, but PlannedOutput#setTypeAndReelType still
 * rejected a REEL output with a null Reel Type, so every "Approve & Assign Shoot" on a REEL row
 * failed with "Reel Type is mandatory when output type is Reel"). This test locks in the fix: a
 * REEL output with no Reel Type must approve cleanly, same as any other Output Type.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdeaReviewReelTypeOptionalTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String PUBLICATION_TARGET_ID = "01926e3e-000a-7000-8000-000000000001";

    @Test
    void approveIdeaWithReelOutputAndNoReelTypeSucceeds() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        JsonNode camUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Reel Camera " + unique + "\",\"email\":\"reel-cam-" + unique
                        + "@kcpcbandhani.local\",\"password\":\"Passw0rd!\",\"businessRoleId\":\""
                        + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"reel type optional test fixture\"}");
        String camId = camUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"reel type optional test grant\"}");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Reel No Type " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        String liveDate = LocalDate.now().plusDays(10).toString();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/reel-no-type-" + unique + "\","
                        + "\"outputs\":[{\"outputType\":\"REEL\","
                        + "\"publicationTargetIds\":[\"" + PUBLICATION_TARGET_ID + "\"]}],"
                        + "\"camerapersonUserIds\":[\"" + camId + "\"]}}");

        assertThat(approved.get("status").asText()).isEqualTo("SA");

        Idea storedIdea = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(storedIdea).orElseThrow();
        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        assertThat(output.getOutputType().name()).isEqualTo("REEL");
        assertThat(output.getReelType()).isNull();
    }
}
