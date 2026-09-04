package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.discussion.repository.StageCommentRepository;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.notification.domain.Notification;
import com.kcpc.mkt.notification.domain.NotificationType;
import com.kcpc.mkt.notification.repository.NotificationRepository;
import com.kcpc.mkt.notification.service.NotificationService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comment notifications on top of the pre-existing Jira-style stage comment threads (see
 * {@link StageDiscussionTest} for the comment feature itself, which this leaves completely
 * unmodified - functionality, UI, authority checks, edit/delete/soft-delete all unchanged).
 *
 * <p>Recipient rule, discovered from and mirroring {@code StageCommentService#requireCommentAuthority}
 * exactly (who may speak on a stage's thread is the mirror image of who is notified about it),
 * per explicit business rule given for this feature:
 * <ul>
 * <li>An Employee's comment (not native CEO/MM authority) notifies every active native-authority
 * user (every CEO_OWNER/MARKETING_MANAGER account) - {@code AuthorizationService#findActiveNativeAuthorityUsers}.</li>
 * <li>An MM/CEO's own comment instead notifies every currently active assignee of THAT SAME stage
 * (Shoot: Cameraperson(s) + Model/Talent "where applicable"; Edit: Editor(s); Publishing:
 * Publisher(s)) - never another stage's assignees, never also a blanket MM/CEO broadcast on top
 * (matches the existing REVIEW_REQUIRED precedent of never notifying native authority for every
 * event, to avoid notification spam).</li>
 * </ul>
 * The commenter is always excluded. Every notification's {@code eventReference} is
 * {@code "COMMENT_ADDED:StageComment:" + comment.getId()} (the existing entity-id-based dedup
 * scheme every other notification type already uses), and its {@code targetTab} is the stage's own
 * {@code ?tab=} value so click-through lands directly on that stage's discussion panel on Content
 * Detail, not just the generic Overview tab.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CommentNotificationTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    StageCommentRepository stageCommentRepository;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    NotificationService notificationService;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String MARKETING_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000002";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String email(String label, long unique) {
        return "cmt-notif-" + label + "-" + unique + "@kcpcbandhani.local";
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"comment notification test fixture\"}");
        return response.get("userId").asText();
    }

    private void grantExecutionPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        HttpResponse<String> grant = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"comment notification test fixture grant\"}");
        if (grant.statusCode() != 201) {
            throw new IllegalStateException("Failed to grant " + permissionCode + " to " + userId + ": " + grant.body());
        }
    }

    /** Idea Review approval carries the initial Shoot Team (Cameraperson(s) + Model/Talent) in one
     * call and transitions straight to Shoot Assigned (SA) - workflow/business logic untouched. */
    private String approveShootAssigned(TestApiClient ceo, String title, List<String> camIds, List<String> talentIds,
                                         String pubId) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        String camJson = camIds.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
        String talentJson = talentIds.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/cmt-notif-" + title.hashCode() + "\","
                        + "\"camerapersonUserIds\":[" + camJson + "],\"talentUserIds\":[" + talentJson + "],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        return plan.getId().toString();
    }

    private ContentPlan planFor(String planId) {
        return contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
    }

    /** Scoped to COMMENT_ADDED only - a test fixture's own assignment naturally fires the
     * pre-existing TASK_ASSIGNED notification too (see NotificationSystemTest), which is not what
     * these comment-notification assertions are about. */
    private List<Notification> notificationsFor(String email) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(user).stream()
                .filter(n -> n.getType() == NotificationType.COMMENT_ADDED)
                .toList();
    }

    /** CEO's own account is shared/seeded and accumulates notifications across the whole suite run
     * (other tests' TASK_COMPLETED etc.) - never assert its raw list size, always scope to this
     * test's own plan, exactly like the earlier "shared CEO state pollution" lesson elsewhere in
     * this suite (see HeaderProfileMenuTest). */
    private List<Notification> commentNotificationsForCeo(ContentPlan plan) {
        return notificationsFor("ceo@kcpcbandhani.local").stream()
                .filter(n -> n.getType() == NotificationType.COMMENT_ADDED)
                .filter(n -> n.getContentPlan() != null && n.getContentPlan().getId().equals(plan.getId()))
                .toList();
    }

    // ------------------------------------------------------------------------------------------

    @Test
    void employeeCommentNotifiesMmAndCeoButNeverTheCommenterOrAnUnrelatedEmployee() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "Cmt Cam", email("cam", unique), CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        String pubId = createUser(ceo, "Cmt Pub", email("pub", unique), PUBLISHER_ROLE_ID);
        grantExecutionPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String mmId = createUser(ceo, "Cmt Mm", email("mm", unique), MARKETING_MANAGER_ROLE_ID);
        // An unrelated employee - no assignment on this plan at all, must never be notified.
        createUser(ceo, "Cmt Unrelated", email("unrelated", unique), CAMERA_PERSON_ROLE_ID);

        String planId = approveShootAssigned(ceo, "Employee Cmt " + unique, List.of(camId), List.of(), pubId);
        ContentPlan plan = planFor(planId);

        TestApiClient camClient = new TestApiClient(port);
        camClient.login(email("cam", unique), "Passw0rd!");
        HttpResponse<String> posted = camClient.postFormAjax("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", "Dupatta ka close-up bhi lena?"));
        assertThat(posted.statusCode()).isEqualTo(200);

        // CEO notified (scoped to this test's own plan - see commentNotificationsForCeo's own comment).
        var ceoNotifs = commentNotificationsForCeo(plan);
        assertThat(ceoNotifs).hasSize(1);
        Notification ceoNotif = ceoNotifs.get(0);
        assertThat(ceoNotif.getMessage()).contains("Cmt Cam").contains(plan.getContentId()).contains("Dupatta ka close-up bhi lena?");
        assertThat(ceoNotif.getTargetTab()).isEqualTo("shoot");
        assertThat(ceoNotif.getEventReference()).startsWith("COMMENT_ADDED:StageComment:");

        // MM notified (a fresh, never-shared account - safe to assert the full list directly).
        var mmNotifs = notificationsFor(email("mm", unique));
        assertThat(mmNotifs).hasSize(1);
        assertThat(mmNotifs.get(0).getType()).isEqualTo(NotificationType.COMMENT_ADDED);
        assertThat(mmNotifs.get(0).getMessage()).contains("Cmt Cam").contains(plan.getContentId());

        // The commenter never receives their own notification.
        assertThat(notificationsFor(email("cam", unique))).isEmpty();
        // An unrelated employee never receives it either.
        assertThat(notificationsFor(email("unrelated", unique))).isEmpty();
    }

    @Test
    void mmCommentNotifiesCurrentStageAssignedUsersNeverMmThemself() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "Mm Cmt Cam", email("mmcam", unique), CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        String pubId = createUser(ceo, "Mm Cmt Pub", email("mmpub", unique), PUBLISHER_ROLE_ID);
        grantExecutionPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String mmId = createUser(ceo, "Mm Cmt Mm", email("mmmm", unique), MARKETING_MANAGER_ROLE_ID);
        String planId = approveShootAssigned(ceo, "Mm Cmt " + unique, List.of(camId), List.of(), pubId);
        ContentPlan plan = planFor(planId);

        TestApiClient mmClient = new TestApiClient(port);
        mmClient.login(email("mmmm", unique), "Passw0rd!");
        HttpResponse<String> posted = mmClient.postFormAjax("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", "Outdoor shot bhi required hai"));
        assertThat(posted.statusCode()).isEqualTo(200);

        var camNotifs = notificationsFor(email("mmcam", unique));
        assertThat(camNotifs).hasSize(1);
        assertThat(camNotifs.get(0).getType()).isEqualTo(NotificationType.COMMENT_ADDED);
        assertThat(camNotifs.get(0).getMessage()).contains("Mm Cmt Mm").contains(plan.getContentId());
        assertThat(camNotifs.get(0).getTargetTab()).isEqualTo("shoot");

        // MM never notifies themself, and CEO (uninvolved in this specific plan/comment) gets nothing new.
        assertThat(notificationsFor(email("mmmm", unique))).isEmpty();
        assertThat(commentNotificationsForCeo(plan)).isEmpty();
    }

    @Test
    void ceoCommentNotifiesCurrentStageAssignedUsersNeverCeoThemself() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "Ceo Cmt Cam", email("ceocam", unique), CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        String pubId = createUser(ceo, "Ceo Cmt Pub", email("ceopub", unique), PUBLISHER_ROLE_ID);
        grantExecutionPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String planId = approveShootAssigned(ceo, "Ceo Cmt " + unique, List.of(camId), List.of(), pubId);
        ContentPlan plan = planFor(planId);

        HttpResponse<String> posted = ceo.postFormAjax("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", "Dupatta aur border close-up bhi lena"));
        assertThat(posted.statusCode()).isEqualTo(200);

        var camNotifs = notificationsFor(email("ceocam", unique));
        assertThat(camNotifs).hasSize(1);
        assertThat(camNotifs.get(0).getMessage()).contains("KCPC CEO").contains(plan.getContentId());
        assertThat(camNotifs.get(0).getTargetTab()).isEqualTo("shoot");

        // CEO never notifies themself for their own comment on this plan.
        assertThat(commentNotificationsForCeo(plan)).isEmpty();
    }

    @Test
    void usersFromUnrelatedStagesAreNotNotified() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "Unrel Cam", email("unrelcam", unique), CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        String editorId = createUser(ceo, "Unrel Editor", email("unreled", unique), VIDEO_EDITOR_ROLE_ID);
        String pubId = createUser(ceo, "Unrel Pub", email("unrelpub", unique), PUBLISHER_ROLE_ID);
        grantExecutionPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String planId = approveShootAssigned(ceo, "Unrel Stage " + unique, List.of(camId), List.of(), pubId);
        // Editor is assigned to the plan's Editing stage (via a direct assignment endpoint), but the
        // comment below is posted on the SHOOTING thread only.
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/assignments", "{\"editorUserId\":\"" + editorId + "\"}");

        HttpResponse<String> posted = ceo.postFormAjax("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", "Shoot-only note"));
        assertThat(posted.statusCode()).isEqualTo(200);

        // The Cameraperson (this stage) is notified; the Editor (a different, unrelated stage) is not,
        // and neither is the Publisher (also unrelated to Shoot).
        assertThat(notificationsFor(email("unrelcam", unique))).hasSize(1);
        assertThat(notificationsFor(email("unreled", unique))).isEmpty();
        assertThat(notificationsFor(email("unrelpub", unique))).isEmpty();
    }

    @Test
    void multipleAssignedUsersAllReceiveTheirOwnNotification() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String cam1 = createUser(ceo, "Multi Cam One", email("multicam1", unique), CAMERA_PERSON_ROLE_ID);
        String cam2 = createUser(ceo, "Multi Cam Two", email("multicam2", unique), CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, cam1, "PERM_18_SHOOT_EXECUTION");
        grantExecutionPermission(ceo, cam2, "PERM_18_SHOOT_EXECUTION");
        String pubId = createUser(ceo, "Multi Pub", email("multipub", unique), PUBLISHER_ROLE_ID);
        grantExecutionPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String planId = approveShootAssigned(ceo, "Multi Cmt " + unique, List.of(cam1, cam2), List.of(), pubId);

        HttpResponse<String> posted = ceo.postFormAjax("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", "Both of you please check the drive folder"));
        assertThat(posted.statusCode()).isEqualTo(200);

        assertThat(notificationsFor(email("multicam1", unique))).hasSize(1);
        assertThat(notificationsFor(email("multicam2", unique))).hasSize(1);
    }

    @Test
    void sameUserQualifyingThroughMultiplePathsReceivesOnlyOneNotification() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        // The same real person holds BOTH the Cameraperson assignment AND a Model/Talent entry on
        // this plan's Shoot stage - two separate reachability paths into currentStageAssignees.
        String dualId = createUser(ceo, "Dual Role Person", email("dual", unique), CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, dualId, "PERM_18_SHOOT_EXECUTION");
        String pubId = createUser(ceo, "Dual Pub", email("dualpub", unique), PUBLISHER_ROLE_ID);
        grantExecutionPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String planId = approveShootAssigned(ceo, "Dual Path " + unique, List.of(dualId), List.of(dualId), pubId);

        HttpResponse<String> posted = ceo.postFormAjax("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", "Checking with both roles here"));
        assertThat(posted.statusCode()).isEqualTo(200);

        // Exactly one notification, not two, despite qualifying as both Cameraperson and Model.
        assertThat(notificationsFor(email("dual", unique))).hasSize(1);
    }

    @Test
    void duplicateProcessingOfTheSameCommentDoesNotCreateDuplicateNotifications() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "Dup Cam", email("dupcam", unique), CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        String pubId = createUser(ceo, "Dup Pub", email("duppub", unique), PUBLISHER_ROLE_ID);
        grantExecutionPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String planId = approveShootAssigned(ceo, "Dup Cmt " + unique, List.of(camId), List.of(), pubId);
        ContentPlan plan = planFor(planId);

        HttpResponse<String> posted = ceo.postFormAjax("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", "Please confirm receipt"));
        assertThat(posted.statusCode()).isEqualTo(200);
        assertThat(notificationsFor(email("dupcam", unique))).hasSize(1);

        // Simulate the comment-notify path being (re)processed a second time for the exact same,
        // already-created comment (e.g. an at-least-once retry) - the underlying dedup mechanism
        // every other notification type already relies on (NotificationService#notify's own
        // (recipient, eventReference) existence check) must make this a no-op.
        var comment = stageCommentRepository.findByContentPlanAndStageOrderByCreatedAtAsc(
                plan, com.kcpc.mkt.identity.domain.LifecycleStage.SHOOTING).get(0);
        User cam = userRepository.findByEmailIgnoreCase(email("dupcam", unique)).orElseThrow();
        notificationService.notify(cam, NotificationType.COMMENT_ADDED, "New Comment",
                "KCPC CEO commented on " + plan.getContentId() + ": \"Please confirm receipt\"",
                plan, "COMMENT_ADDED:StageComment:" + comment.getId(), "shoot");

        assertThat(notificationsFor(email("dupcam", unique))).hasSize(1);
    }

    @Test
    void markReadContinuesToWorkForCommentNotifications() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "Mark Read Cam", email("markreadcam", unique), CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        String pubId = createUser(ceo, "Mark Read Pub", email("markreadpub", unique), PUBLISHER_ROLE_ID);
        grantExecutionPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String planId = approveShootAssigned(ceo, "Mark Read " + unique, List.of(camId), List.of(), pubId);

        assertThat(ceo.postFormAjax("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", "Please review")).statusCode()).isEqualTo(200);

        TestApiClient camClient = new TestApiClient(port);
        camClient.login(email("markreadcam", unique), "Passw0rd!");

        Notification notif = notificationsFor(email("markreadcam", unique)).get(0);
        assertThat(notif.isUnread()).isTrue();

        HttpResponse<String> markRead = camClient.postFormAjax(
                "/app/notifications/" + notif.getId() + "/read", Map.of());
        assertThat(markRead.statusCode()).isEqualTo(200);

        Notification reloaded = notificationRepository.findById(notif.getId()).orElseThrow();
        assertThat(reloaded.isUnread()).isFalse();
        assertThat(reloaded.getReadAt()).isNotNull();
    }

    @Test
    void longCommentIsTruncatedInThePreviewButFullTextStaysInTheThread() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "Long Cam", email("longcam", unique), CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        String pubId = createUser(ceo, "Long Pub", email("longpub", unique), PUBLISHER_ROLE_ID);
        grantExecutionPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String planId = approveShootAssigned(ceo, "Long Cmt " + unique, List.of(camId), List.of(), pubId);
        ContentPlan plan = planFor(planId);

        String longText = "A".repeat(180);
        assertThat(ceo.postFormAjax("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", longText)).statusCode()).isEqualTo(200);

        Notification notif = notificationsFor(email("longcam", unique)).get(0);
        assertThat(notif.getMessage()).contains("…");
        assertThat(notif.getMessage().length()).isLessThan(longText.length());

        // The full, untruncated text is still exactly what is stored/shown in the actual thread.
        var comment = stageCommentRepository.findByContentPlanAndStageOrderByCreatedAtAsc(
                plan, com.kcpc.mkt.identity.domain.LifecycleStage.SHOOTING).get(0);
        assertThat(comment.getCommentText()).isEqualTo(longText);
    }

    /**
     * Walks the pipeline forward into Edit and Publishing to prove the same recipient/targetTab
     * rule generalizes past Shoot: each stage's comment only reaches that stage's own current
     * assignee(s) (never a co-existing but different-stage assignee, e.g. the Cameraperson from
     * Shoot), and each notification's targetTab/click-through route matches its own stage.
     */
    @Test
    void editAndPublishingStageCommentsNotifyTheRightPeopleWithTheirOwnTargetTab() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "Pipeline Cam", email("pipecam", unique), CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        String editorId = createUser(ceo, "Pipeline Editor", email("pipeed", unique), VIDEO_EDITOR_ROLE_ID);
        grantExecutionPermission(ceo, editorId, "PERM_19_EDIT_EXECUTION");
        String pubId = createUser(ceo, "Pipeline Pub", email("pipepub", unique), PUBLISHER_ROLE_ID);
        grantExecutionPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String planId = approveShootAssigned(ceo, "Pipeline " + unique, List.of(camId), List.of(), pubId);
        ContentPlan plan = planFor(planId);

        TestApiClient camClient = new TestApiClient(port);
        camClient.login(email("pipecam", unique), "Passw0rd!");
        assertThat(camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode()).isEqualTo(200);
        assertThat(camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "").statusCode()).isEqualTo(200);
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + editorId + "\"],\"leadEditorUserId\":\"" + editorId + "\"}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("EA");

        // Edit stage comment (posted by CEO) - only the Editor is notified, never the Shoot Cameraperson.
        assertThat(ceo.postFormAjax("/app/deliverables/" + planId + "/editing/comments",
                Map.of("commentText", "Please trim the intro")).statusCode()).isEqualTo(200);
        var editNotifs = notificationsFor(email("pipeed", unique));
        assertThat(editNotifs).hasSize(1);
        assertThat(editNotifs.get(0).getTargetTab()).isEqualTo("edit");
        assertThat(notificationsFor(email("pipecam", unique))).isEmpty();

        TestApiClient editorClient = new TestApiClient(port);
        editorClient.login(email("pipeed", unique), "Passw0rd!");
        assertThat(editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "").statusCode()).isEqualTo(200);
        assertThat(editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "").statusCode()).isEqualTo(200);
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editorId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

        // Publishing stage comment (posted by CEO) - only the Publisher is notified.
        assertThat(ceo.postFormAjax("/app/deliverables/" + planId + "/publishing/comments",
                Map.of("commentText", "Caption ready to go")).statusCode()).isEqualTo(200);
        var pubNotifs = notificationsFor(email("pipepub", unique));
        assertThat(pubNotifs).hasSize(1);
        assertThat(pubNotifs.get(0).getTargetTab()).isEqualTo("publishing");
        assertThat(notificationsFor(email("pipeed", unique))).hasSize(1); // unchanged - still just the earlier one
    }
}
