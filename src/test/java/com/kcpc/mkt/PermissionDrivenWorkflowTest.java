package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.PermissionGrantRepository;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.marks.domain.RoleType;
import com.kcpc.mkt.marks.repository.PersonalMarkAttributionRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated regression suite for the permission-driven multi-function workflow (KCPC_PERMISSION_
 * DRIVEN_WORKFLOW_IMPLEMENTATION_SPEC.md §43) - written against the target behavior directly,
 * not merely repaired from pre-existing fixtures (see AssignmentPickerTest/HighPriorityEdgeCaseTest/
 * etc., which were updated in place to keep passing under the new eligibility rules). Every test
 * here exists specifically to prove a permission-driven-model guarantee that did not exist, or
 * behaved differently, before PERM_18/PERM_19/OperationalEligibilityService/WorkspaceAccessService.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PermissionDrivenWorkflowTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PermissionGrantRepository permissionGrantRepository;
    @Autowired
    ShootingAssignmentRepository shootingAssignmentRepository;
    @Autowired
    EditingAssignmentRepository editingAssignmentRepository;
    @Autowired
    PersonalMarkAttributionRepository markAttributionRepository;

    private static final String HR_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000003";
    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    // ================================================================== A. Shoot candidate eligibility

    @Test
    void shootCandidateEligibility_hrWithPerm18Appears_userWithoutItDoesNot() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String eligibleId = createUser(ceo, "Cand Eligible A", "cand-eligible-a-" + unique + "@kcpcbandhani.local", HR_MANAGER_ROLE_ID);
        grant(ceo, eligibleId, "PERM_18_SHOOT_EXECUTION");
        String ineligibleId = createUser(ceo, "Cand Ineligible B", "cand-ineligible-b-" + unique + "@kcpcbandhani.local", HR_MANAGER_ROLE_ID);
        String planId = approveIdeaAndGetContentPlanId(ceo, "Candidate Eligibility Flow " + unique);

        String page = ceo.get("/app/deliverables/" + planId).body();
        assertThat(page).contains("value=\"" + eligibleId + "\"");
        assertThat(page).doesNotContain("value=\"" + ineligibleId + "\"");
    }

    @Test
    void shootCandidateEligibility_revokedGrantRemovesUserFromPicker() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String userId = createUser(ceo, "Cand Revoke", "cand-revoke-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grant(ceo, userId, "PERM_18_SHOOT_EXECUTION");
        String planId = approveIdeaAndGetContentPlanId(ceo, "Candidate Revoke Flow " + unique);

        assertThat(ceo.get("/app/deliverables/" + planId).body()).contains("value=\"" + userId + "\"");

        revoke(ceo, userId, "PERM_18_SHOOT_EXECUTION");

        assertThat(ceo.get("/app/deliverables/" + planId).body()).doesNotContain("value=\"" + userId + "\"");
    }

    @Test
    void shootCandidateEligibility_stageRestrictedGrantOutsideShootingDoesNotQualify() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String userId = createUser(ceo, "Cand OutOfScope", "cand-outofscope-" + unique + "@kcpcbandhani.local", HR_MANAGER_ROLE_ID);
        // A PERM_18 grant restricted to EDITING only must not make this user a SHOOTING candidate.
        HttpResponse<String> grantResp = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"STAGE_RESTRICTED\",\"stages\":[\"EDITING\"],\"reason\":\"out-of-scope test\"}");
        assertThat(grantResp.statusCode()).isEqualTo(201);
        String planId = approveIdeaAndGetContentPlanId(ceo, "Candidate OutOfScope Flow " + unique);

        assertThat(ceo.get("/app/deliverables/" + planId).body()).doesNotContain("value=\"" + userId + "\"");

        HttpResponse<String> directAssign = ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments",
                "{\"cameramanUserId\":\"" + userId + "\"}");
        assertThat(directAssign.statusCode()).as("A grant scoped to a different stage must not satisfy Shoot eligibility").isEqualTo(403);
    }

    @Test
    void shootCandidateEligibility_ceoWithoutExplicitGrantNeverQualifies() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User ceoUser = userRepository.findByEmailIgnoreCase("ceo@kcpcbandhani.local").orElseThrow();
        String planId = approveIdeaAndGetContentPlanId(ceo, "Candidate CEO Native Flow " + unique);

        assertThat(ceo.get("/app/deliverables/" + planId).body())
                .doesNotContain("value=\"" + ceoUser.getId() + "\"");
    }

    // ================================================================== B/C. Assignment backend (Shoot + Edit)

    @Test
    void shootAssignmentBackend_eligibleAccepted_ineligibleRejected_inactiveRejected() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String eligibleId = createUser(ceo, "Assign Eligible", "assign-eligible-" + unique + "@kcpcbandhani.local", HR_MANAGER_ROLE_ID);
        grant(ceo, eligibleId, "PERM_18_SHOOT_EXECUTION");
        String ineligibleId = createUser(ceo, "Assign Ineligible", "assign-ineligible-" + unique + "@kcpcbandhani.local", HR_MANAGER_ROLE_ID);
        String inactiveId = createUser(ceo, "Assign Inactive", "assign-inactive-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grant(ceo, inactiveId, "PERM_18_SHOOT_EXECUTION");
        deactivate(ceo, inactiveId);

        String planId = approveIdeaAndGetContentPlanId(ceo, "Assign Backend Flow " + unique);

        assertThat(ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments",
                "{\"cameramanUserId\":\"" + eligibleId + "\"}").statusCode())
                .as("Eligible (PERM_18-holding, active) assignee accepted").isEqualTo(200);
        assertThat(ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments",
                "{\"cameramanUserId\":\"" + ineligibleId + "\"}").statusCode())
                .as("No PERM_18 - direct API assignment rejected regardless of UI picker").isEqualTo(403);
        assertThat(ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments",
                "{\"cameramanUserId\":\"" + inactiveId + "\"}").statusCode())
                .as("Deactivated user, even with a PERM_18 grant on record, must be rejected").isEqualTo(403);
    }

    @Test
    void editAssignmentBackend_eligibleAccepted_ineligibleRejected_inactiveRejected() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String eligibleId = createUser(ceo, "Edit Assign Eligible", "editassign-eligible-" + unique + "@kcpcbandhani.local", HR_MANAGER_ROLE_ID);
        grant(ceo, eligibleId, "PERM_19_EDIT_EXECUTION");
        String ineligibleId = createUser(ceo, "Edit Assign Ineligible", "editassign-ineligible-" + unique + "@kcpcbandhani.local", HR_MANAGER_ROLE_ID);
        String inactiveId = createUser(ceo, "Edit Assign Inactive", "editassign-inactive-" + unique + "@kcpcbandhani.local", VIDEO_EDITOR_ROLE_ID);
        grant(ceo, inactiveId, "PERM_19_EDIT_EXECUTION");
        deactivate(ceo, inactiveId);

        String planId = advanceToShootApproved(ceo, unique, "Edit Assign Backend Flow");

        assertThat(ceo.post("/api/v1/content-plans/" + planId + "/editing/assignments",
                "{\"editorUserId\":\"" + eligibleId + "\"}").statusCode()).isEqualTo(200);
        assertThat(ceo.post("/api/v1/content-plans/" + planId + "/editing/assignments",
                "{\"editorUserId\":\"" + ineligibleId + "\"}").statusCode()).isEqualTo(403);
        assertThat(ceo.post("/api/v1/content-plans/" + planId + "/editing/assignments",
                "{\"editorUserId\":\"" + inactiveId + "\"}").statusCode()).isEqualTo(403);
    }

    // ================================================================== D. Publishing

    @Test
    void publishingEligibility_nonPublisherRoleWithValidPerm08_publisherRoleWithoutValidPerm08() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String hrId = createUser(ceo, "Pub HR Eligible", "pub-hr-eligible-" + unique + "@kcpcbandhani.local", HR_MANAGER_ROLE_ID);
        grant(ceo, hrId, "PERM_08_PUBLISHING_EXECUTION");
        String pubRoleNoGrantId = createUser(ceo, "Pub Role NoGrant", "pub-role-nogrant-" + unique + "@kcpcbandhani.local", PUBLISHER_ROLE_ID);
        String planId = advanceToReadyForPublishing(ceo, unique, "Publishing Eligibility Flow");

        String page = ceo.get("/app/deliverables/" + planId).body();
        assertThat(page).as("Non-Publisher Business Role, but holding valid PERM_08, appears as a candidate")
                .contains("value=\"" + hrId + "\"");
        assertThat(page).as("Publisher Business Role WITHOUT a valid PERM_08 grant does not appear as a candidate")
                .doesNotContain("value=\"" + pubRoleNoGrantId + "\"");

        assertThat(ceo.post("/api/v1/content-plans/" + planId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + pubRoleNoGrantId + "\"}").statusCode())
                .as("Direct POST for the Publisher-role-but-no-PERM_08 user is rejected regardless of Business Role")
                .isEqualTo(403);
    }

    @Test
    void publishingExecution_activeAssignmentWorks_revokeAfterAssignmentBlocksExecution() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "pub-revoke-" + unique + "@kcpcbandhani.local";
        String userId = createUser(ceo, "Pub Revoke", email, HR_MANAGER_ROLE_ID);
        grant(ceo, userId, "PERM_08_PUBLISHING_EXECUTION");
        String planId = advanceToReadyForPublishing(ceo, unique, "Publishing Revoke Flow");
        ceo.post("/api/v1/content-plans/" + planId + "/publishing/assignments", "{\"publisherUserId\":\"" + userId + "\"}");

        TestApiClient holder = loginNewClient(email);
        assertThat(holder.post("/api/v1/content-plans/" + planId + "/publishing/start", "").statusCode())
                .as("Valid PERM_08 + active Publishing assignment allows Start Publishing").isEqualTo(200);

        // Second content plan to prove revoke blocks a FRESH attempt (Start already consumed above).
        String email2 = "pub-revoke2-" + unique + "@kcpcbandhani.local";
        String userId2 = createUser(ceo, "Pub Revoke2", email2, HR_MANAGER_ROLE_ID);
        grant(ceo, userId2, "PERM_08_PUBLISHING_EXECUTION");
        String planId2 = advanceToReadyForPublishing(ceo, unique + 1, "Publishing Revoke2 Flow");
        ceo.post("/api/v1/content-plans/" + planId2 + "/publishing/assignments", "{\"publisherUserId\":\"" + userId2 + "\"}");
        revoke(ceo, userId2, "PERM_08_PUBLISHING_EXECUTION");
        TestApiClient holder2 = loginNewClient(email2);
        assertThat(holder2.post("/api/v1/content-plans/" + planId2 + "/publishing/start", "").statusCode())
                .as("Active Publishing assignment alone, after PERM_08 is revoked, must not allow Start Publishing")
                .isEqualTo(403);
    }

    // ================================================================== E. Reassignment

    @Test
    void reassignment_usesSameEligibilityRuleAsInitialAssignment_notBusinessRole() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String originalCamId = createUser(ceo, "Reassign Original Cam", "reassign-orig-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grant(ceo, originalCamId, "PERM_18_SHOOT_EXECUTION");
        String hrEligibleId = createUser(ceo, "Reassign HR Eligible", "reassign-hr-elig-" + unique + "@kcpcbandhani.local", HR_MANAGER_ROLE_ID);
        grant(ceo, hrEligibleId, "PERM_18_SHOOT_EXECUTION");
        String camNoGrantId = createUser(ceo, "Reassign Cam NoGrant", "reassign-cam-nogrant-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);

        String planId = approveIdeaAndGetContentPlanId(ceo, "Reassignment Parity Flow " + unique);
        ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + originalCamId + "\"}");
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();

        // A Camera Person WITHOUT PERM_18 is rejected as a reassignment target - Business Role alone
        // is no longer sufficient (this is the exact assertion that changed under the new model).
        assertThat(ceo.post("/api/v1/content-plans/" + planId + "/reassign",
                "{\"taskStage\":\"SHOOTING\",\"newAssigneeUserIds\":[\"" + camNoGrantId + "\"],\"reason\":\"no grant\"}")
                .statusCode()).isEqualTo(403);
        assertThat(shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .anyMatch(a -> a.getCameraperson().getId().equals(UUID.fromString(camNoGrantId)))).isFalse();
        assertThat(shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .anyMatch(a -> a.getCameraperson().getId().equals(UUID.fromString(originalCamId))))
                .as("Rejected reassignment must not have disturbed the original active assignment (transactional rollback)")
                .isTrue();

        // An HR Manager WITH PERM_18 is accepted as a reassignment target - the same rule initial
        // assignment already uses, proving the two paths no longer diverge.
        assertThat(ceo.post("/api/v1/content-plans/" + planId + "/reassign",
                "{\"taskStage\":\"SHOOTING\",\"newAssigneeUserIds\":[\"" + hrEligibleId + "\"],\"reason\":\"has grant\"}")
                .statusCode()).isEqualTo(200);
        List<com.kcpc.mkt.production.domain.ShootingAssignment> active =
                shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getCameraperson().getId()).isEqualTo(UUID.fromString(hrEligibleId));
    }

    // ================================================================== F. Multi-function user

    @Test
    void multiFunctionUser_hrWithShootAndEditPermissionsSeesBothTaskTypesInMyWorkWithoutBusinessRoleChange() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "multifunc-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(ceo, "MultiFunc HR", email, HR_MANAGER_ROLE_ID);
        grant(ceo, hrId, "PERM_18_SHOOT_EXECUTION");
        grant(ceo, hrId, "PERM_19_EDIT_EXECUTION");

        String shootPlanId = approveIdeaAndGetContentPlanId(ceo, "MultiFunc Shoot Flow " + unique, hrId);

        String editPlanId = advanceToShootApproved(ceo, unique + 1, "MultiFunc Edit Flow");
        ceo.post("/api/v1/content-plans/" + editPlanId + "/editing/assignments", "{\"editorUserId\":\"" + hrId + "\"}");

        ContentPlan shootPlan = contentPlanRepository.findById(UUID.fromString(shootPlanId)).orElseThrow();
        ContentPlan editPlan = contentPlanRepository.findById(UUID.fromString(editPlanId)).orElseThrow();

        TestApiClient hrClient = loginNewClient(email);
        HttpResponse<String> myWork = hrClient.get("/app/my-work");
        assertThat(myWork.statusCode()).isEqualTo(200);
        assertThat(myWork.body()).as("Both stages' tasks appear for this one multi-function user")
                .contains(shootPlan.getContentId()).contains(editPlan.getContentId());
        assertThat(myWork.body()).as("Stage tabs for both Shoot and Edit are present")
                .contains("data-tab=\"shoot\"").contains("data-tab=\"edit\"");

        // Correct stage actions each work independently.
        assertThat(hrClient.post("/api/v1/content-plans/" + shootPlanId + "/shooting/start", "").statusCode()).isEqualTo(200);
        assertThat(hrClient.post("/api/v1/content-plans/" + editPlanId + "/editing/start", "").statusCode()).isEqualTo(200);

        // No Business Role mutation occurred anywhere in the process.
        User reloaded = userRepository.findById(UUID.fromString(hrId)).orElseThrow();
        assertThat(reloaded.getBusinessRole().getRoleName()).isEqualTo("HR Manager");
    }

    // ================================================================== G. Marks

    @Test
    void marks_sameUserReceivesBothCameramanAndEditorMarksIndependently() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "bothmarks-" + unique + "@kcpcbandhani.local";
        String userId = createUser(ceo, "BothMarks User", email, HR_MANAGER_ROLE_ID);
        grant(ceo, userId, "PERM_18_SHOOT_EXECUTION");
        grant(ceo, userId, "PERM_19_EDIT_EXECUTION");
        TestApiClient holder = loginNewClient(email);

        // Shoot cycle -> Cameraperson mark. A throwaway Editor folds into this same Approve call
        // (workflow redesign) - unrelated to this test's own Cameraperson/Editor mark assertions.
        String shootPlanId = approveIdeaAndGetContentPlanId(ceo, "BothMarks Shoot Flow " + unique, userId);
        holder.post("/api/v1/content-plans/" + shootPlanId + "/shooting/start", "");
        holder.post("/api/v1/content-plans/" + shootPlanId + "/shooting/review/submit", "");
        String throwawayEdId = createUser(ceo, "BothMarks Throwaway Ed", "bothmarks-throwaway-ed-" + unique + "@kcpcbandhani.local",
                VIDEO_EDITOR_ROLE_ID);
        grant(ceo, throwawayEdId, "PERM_19_EDIT_EXECUTION");
        ceo.postJson("/api/v1/content-plans/" + shootPlanId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + userId + "\"],"
                        + "\"editorUserIds\":[\"" + throwawayEdId + "\"],\"leadEditorUserId\":\"" + throwawayEdId + "\"}");

        // Independent Edit cycle -> Editor mark, same user. A throwaway Publisher folds into the
        // Edit Review Approve call similarly.
        String editPlanId = advanceToShootApproved(ceo, unique + 1, "BothMarks Edit Flow");
        ceo.post("/api/v1/content-plans/" + editPlanId + "/editing/assignments", "{\"editorUserId\":\"" + userId + "\"}");
        holder.post("/api/v1/content-plans/" + editPlanId + "/editing/start", "");
        holder.post("/api/v1/content-plans/" + editPlanId + "/editing/review/submit", "");
        String throwawayPubId = createUser(ceo, "BothMarks Throwaway Pub", "bothmarks-throwaway-pub-" + unique + "@kcpcbandhani.local",
                PUBLISHER_ROLE_ID);
        grant(ceo, throwawayPubId, "PERM_08_PUBLISHING_EXECUTION");
        ceo.postJson("/api/v1/content-plans/" + editPlanId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + userId + "\"],"
                        + "\"publisherUserIds\":[\"" + throwawayPubId + "\"]}");

        User user = userRepository.findById(UUID.fromString(userId)).orElseThrow();
        var marks = markAttributionRepository.findByRecipient(user);
        assertThat(marks.stream().filter(m -> m.getRoleType() == RoleType.CAMERAPERSON)).hasSize(1);
        assertThat(marks.stream().filter(m -> m.getRoleType() == RoleType.EDITOR)).hasSize(1);
    }

    // ================================================================== H. Permission revoke after assignment

    @Test
    void permissionRevokeAfterAssignment_taskStaysVisibleExecutionBlockedWarningRendered() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "revoke-warning-" + unique + "@kcpcbandhani.local";
        String userId = createUser(ceo, "Revoke Warning User", email, HR_MANAGER_ROLE_ID);
        grant(ceo, userId, "PERM_18_SHOOT_EXECUTION");
        String planId = approveIdeaAndGetContentPlanId(ceo, "Revoke Warning Flow " + unique, userId);
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();

        revoke(ceo, userId, "PERM_18_SHOOT_EXECUTION");

        TestApiClient holder = loginNewClient(email);
        HttpResponse<String> myWork = holder.get("/app/my-work");
        assertThat(myWork.statusCode()).as("My Work stays reachable (active assignment still exists)").isEqualTo(200);
        assertThat(myWork.body()).as("The assignment/task itself remains visible, never silently hidden")
                .contains(plan.getContentId());
        assertThat(myWork.body()).as("A clear permission-removed/reassignment-required message is shown")
                .contains("Execution permission removed. This task requires reassignment or permission restoration.");

        assertThat(shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).as("The assignment row itself is never deleted").hasSize(1);
        assertThat(holder.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode())
                .as("Execution is blocked").isEqualTo(403);
    }

    // ================================================================== I. Workflow participation override

    @Test
    void workflowParticipationOverride_fullLifecycle() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "participation-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(ceo, "Participation HR", email, HR_MANAGER_ROLE_ID);
        // HR Manager's Business Role has participates_in_workflow = false by seed data.
        grant(ceo, hrId, "PERM_18_SHOOT_EXECUTION");
        String planId = approveIdeaAndGetContentPlanId(ceo, "Participation Flow " + unique, hrId);
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();

        TestApiClient holder = loginNewClient(email);

        // My Work reachable, Shoot task visible when assigned.
        HttpResponse<String> myWorkWithGrant = holder.get("/app/my-work");
        assertThat(myWorkWithGrant.statusCode()).isEqualTo(200);
        assertThat(myWorkWithGrant.body()).contains(plan.getContentId());

        // Unrelated management screens remain denied.
        assertThat(holder.get("/app/pipeline").statusCode()).isEqualTo(302);
        assertThat(holder.get("/app/admin/users").statusCode()).isEqualTo(302);

        // After permission revoke with active assignment: My Work remains reachable, read-only.
        revoke(ceo, hrId, "PERM_18_SHOOT_EXECUTION");
        HttpResponse<String> myWorkAfterRevoke = holder.get("/app/my-work");
        assertThat(myWorkAfterRevoke.statusCode()).as("My Work remains reachable via the active assignment alone").isEqualTo(200);
        assertThat(myWorkAfterRevoke.body()).contains(plan.getContentId());
        assertThat(holder.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode()).isEqualTo(403);

        // After assignment ends and no relevant permission remains: idea-only workspace restored.
        // (Shooting Assignment removal is only permitted during Planning per PlanningService -
        // this plan is already past Planning here, so the assignment is ended directly, exactly
        // as the same "reassign" admin action already does internally, to reach the end-state.)
        for (var assignment : shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan)) {
            assignment.end();
            shootingAssignmentRepository.save(assignment);
        }
        HttpResponse<String> myWorkAfterUnassign = holder.get("/app/my-work");
        assertThat(myWorkAfterUnassign.statusCode()).as("No permission, no assignment - back to idea-only").isEqualTo(302);
        assertThat(myWorkAfterUnassign.headers().firstValue("Location").orElseThrow()).contains("/app/ideas");
    }

    // ================================================================== J. Reviews navigation

    @Test
    void reviewsNavigation_shootReviewOnlyPermissionShowsOnlyShootTabAndDeniesDirectUrlToOthers() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "reviewnav-" + unique + "@kcpcbandhani.local";
        String userId = createUser(ceo, "Review Nav User", email, HR_MANAGER_ROLE_ID);
        grant(ceo, userId, "PERM_05_SHOOT_REVIEW");
        TestApiClient holder = loginNewClient(email);

        HttpResponse<String> reviewsPage = holder.get("/app/reviews");
        assertThat(reviewsPage.statusCode()).isEqualTo(200);
        assertThat(reviewsPage.body()).contains("data-tab=\"shoot\"");
        assertThat(reviewsPage.body()).doesNotContain("data-tab=\"planning\"").doesNotContain("data-tab=\"edit\"");

        // Direct URL to an unauthorized tab falls back to the authorized one, not the requested one.
        HttpResponse<String> directToEdit = holder.get("/app/reviews?tab=edit");
        assertThat(directToEdit.statusCode()).isEqualTo(200);
        assertThat(directToEdit.body()).as("Falls back to the one authorized tab (shoot), not the requested (edit) one")
                .contains("reviews-tab active\" href=\"/app/reviews?tab=shoot\"");
    }

    // ================================================================== K. Team Workload / KPI attribution

    @Test
    void teamWorkload_sameEmployeeAppearsUnderBothShootAndEditFilterableByBusinessRole() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String hrId = createUser(ceo, "Workload MultiStage HR", "workload-multistage-" + unique + "@kcpcbandhani.local", HR_MANAGER_ROLE_ID);
        grant(ceo, hrId, "PERM_18_SHOOT_EXECUTION");
        grant(ceo, hrId, "PERM_19_EDIT_EXECUTION");

        String shootPlanId = approveIdeaAndGetContentPlanId(ceo, "Workload Shoot Flow " + unique, hrId);

        String editPlanId = advanceToShootApproved(ceo, unique + 1, "Workload Edit Flow");
        ceo.post("/api/v1/content-plans/" + editPlanId + "/editing/assignments", "{\"editorUserId\":\"" + hrId + "\"}");

        HttpResponse<String> shootWorkload = ceo.get("/app/reports/workload?stage=Shoot");
        assertThat(shootWorkload.body()).as("Appears under Shoot based on assignment").contains("Workload MultiStage HR");

        HttpResponse<String> editWorkload = ceo.get("/app/reports/workload?stage=Edit");
        assertThat(editWorkload.body()).as("Same employee also appears under Edit").contains("Workload MultiStage HR");

        // Filterable by their real Business Role, still shown correctly (HR Manager, not fabricated).
        HttpResponse<String> filteredByRole = ceo.get("/app/reports/workload?businessRole=HR%20Manager");
        assertThat(filteredByRole.body()).contains("Workload MultiStage HR").contains("HR Manager");
    }

    @Test
    void kpiAttribution_historicalMarksRemainStageSpecificAfterPermissionRevocation() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "kpi-history-" + unique + "@kcpcbandhani.local";
        String userId = createUser(ceo, "Kpi History User", email, HR_MANAGER_ROLE_ID);
        grant(ceo, userId, "PERM_18_SHOOT_EXECUTION");
        TestApiClient holder = loginNewClient(email);

        String planId = approveIdeaAndGetContentPlanId(ceo, "Kpi History Flow " + unique, userId);
        holder.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        holder.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        String throwawayEdId = createUser(ceo, "Kpi History Throwaway Ed", "kpi-history-throwaway-ed-" + unique + "@kcpcbandhani.local",
                VIDEO_EDITOR_ROLE_ID);
        grant(ceo, throwawayEdId, "PERM_19_EDIT_EXECUTION");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + userId + "\"],"
                        + "\"editorUserIds\":[\"" + throwawayEdId + "\"],\"leadEditorUserId\":\"" + throwawayEdId + "\"}");

        // Revoking the permission afterward must not erase the historical mark/contribution.
        revoke(ceo, userId, "PERM_18_SHOOT_EXECUTION");

        User user = userRepository.findById(UUID.fromString(userId)).orElseThrow();
        var marks = markAttributionRepository.findByRecipient(user);
        assertThat(marks.stream().filter(m -> m.getRoleType() == RoleType.CAMERAPERSON)).hasSize(1);
    }

    // ------------------------------------------------------------------ helpers

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private TestApiClient loginNewClient(String email) throws Exception {
        TestApiClient client = new TestApiClient(port);
        client.login(email, "Passw0rd!");
        return client;
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"permission-driven workflow regression test\"}");
        return response.get("userId").asText();
    }

    private void grant(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        HttpResponse<String> resp = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"permission-driven workflow regression test grant\"}");
        if (resp.statusCode() != 201) {
            throw new IllegalStateException("Grant failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    private void deactivate(TestApiClient ceo, String userId) throws Exception {
        HttpResponse<String> resp = ceo.post("/api/v1/admin/users/" + userId + "/deactivate",
                "{\"reason\":\"permission-driven workflow regression test deactivation\"}");
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Deactivate failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    private void revoke(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        User user = userRepository.findById(UUID.fromString(userId)).orElseThrow();
        var grant = permissionGrantRepository.findByGrantee(user).stream()
                .filter(g -> g.getPermission() == OperationalPermission.valueOf(permissionCode))
                .filter(g -> g.isActive())
                .findFirst().orElseThrow();
        HttpResponse<String> resp = ceo.post("/api/v1/admin/permission-grants/" + grant.getId() + "/revoke",
                "{\"reason\":\"permission-driven workflow regression test revoke\"}");
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Revoke failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    /** Workflow redesign: Idea Review approval always requires at least one Cameraperson - this
     * default overload creates a throwaway one (irrelevant to the caller's assertions) so plain
     * "just give me an approved plan" call sites don't need to care. */
    private String approveIdeaAndGetContentPlanId(TestApiClient ceo, String title) throws Exception {
        long unique = Instant.now().toEpochMilli() + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000);
        String camId = createUser(ceo, "Default Cam " + unique, "pdw-default-cam-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grant(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        return approveIdeaAndGetContentPlanId(ceo, title, camId);
    }

    /** Workflow redesign: Idea Review approval carries every former Planning field (including the
     * initial Shoot Team) in one call and transitions straight to Shoot Assigned (SA), never
     * PL/PLRV/PLAP - the given cameraperson must already hold an active PERM_18_SHOOT_EXECUTION grant. */
    private String approveIdeaAndGetContentPlanId(TestApiClient ceo, String title, String camId) throws Exception {
        long publisherUnique = Instant.now().toEpochMilli() + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000);
        String pubId = createUser(ceo, "Pdw Default Pub " + publisherUnique, "pdw-default-pub-" + publisherUnique + "@kcpcbandhani.local", PUBLISHER_ROLE_ID);
        grant(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/pdw-" + Instant.now().toEpochMilli() + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pubId + "\"]}}");
        Idea ideaEntity = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(ideaEntity).orElseThrow();
        return plan.getId().toString();
    }

    /** Idea -> approved -> Shoot Assigned -> Started -> Submitted -> Approved -> Edit Assigned (EA),
     * fresh Cameraperson with PERM_18. Workflow redesign: Shoot Review Approve now folds in Editor
     * team assignment directly (ShootingService#decideShootReview), so the plan lands on EA (never
     * a resting SAP) via a throwaway Editor unrelated to callers' own assertions - the standalone
     * /editing/assignments endpoint most callers exercise stays equally valid at EA
     * (EditingService#assignEditor's window is SAP-or-EA, unchanged). */
    private String advanceToShootApproved(TestApiClient ceo, long unique, String title) throws Exception {
        String camEmail = "pdw-camflow-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "Pdw Cam Flow", camEmail, CAMERA_PERSON_ROLE_ID);
        grant(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        String planId = approveIdeaAndGetContentPlanId(ceo, title + " " + unique, camId);
        TestApiClient cam = loginNewClient(camEmail);
        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        String throwawayEdEmail = "pdw-throwaway-ed-" + unique + "@kcpcbandhani.local";
        String throwawayEdId = createUser(ceo, "Pdw Throwaway Ed", throwawayEdEmail, VIDEO_EDITOR_ROLE_ID);
        grant(ceo, throwawayEdId, "PERM_19_EDIT_EXECUTION");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + throwawayEdId + "\"],\"leadEditorUserId\":\"" + throwawayEdId + "\"}");
        return planId;
    }

    /** Idea -> ... -> RFP (Edit approved), fresh Cameraperson+Editor with PERM_18/19. Workflow
     * redesign: Edit Review Approve now folds in Publisher team assignment directly
     * (EditingService#decideEditReview) via a throwaway Publisher unrelated to callers' own
     * assertions - the standalone /publishing/assignments endpoint most callers exercise remains
     * unaffected (Publishing allows multiple simultaneously active Publishers). */
    private String advanceToReadyForPublishing(TestApiClient ceo, long unique, String title) throws Exception {
        String planId = advanceToShootApproved(ceo, unique, title);
        String edEmail = "pdw-edflow-" + unique + "@kcpcbandhani.local";
        String edId = createUser(ceo, "Pdw Ed Flow", edEmail, VIDEO_EDITOR_ROLE_ID);
        grant(ceo, edId, "PERM_19_EDIT_EXECUTION");
        ceo.post("/api/v1/content-plans/" + planId + "/editing/assignments", "{\"editorUserId\":\"" + edId + "\"}");
        TestApiClient ed = loginNewClient(edEmail);
        ed.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        ed.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        String throwawayPubEmail = "pdw-throwaway-pub-" + unique + "@kcpcbandhani.local";
        String throwawayPubId = createUser(ceo, "Pdw Throwaway Pub", throwawayPubEmail, PUBLISHER_ROLE_ID);
        grant(ceo, throwawayPubId, "PERM_08_PUBLISHING_EXECUTION");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edId + "\"],"
                        + "\"publisherUserIds\":[\"" + throwawayPubId + "\"]}");
        return planId;
    }
}
