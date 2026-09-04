package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.notification.domain.Notification;
import com.kcpc.mkt.notification.domain.NotificationType;
import com.kcpc.mkt.notification.repository.NotificationRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Role-based Notification System (MVP: TASK_ASSIGNED, TASK_REASSIGNED(_AWAY), REVIEW_REQUIRED,
 * REVIEW_APPROVED, CHANGES_REQUIRED, TASK_COMPLETED, TASK_RESCHEDULED, TASK_CANCELLED). Deadline
 * Approaching/Task Delayed are deliberately out of MVP scope (no scheduler infrastructure exists
 * in this app - user's own explicit decision) and are not covered here. Every notification-
 * generating call site is a pure side effect layered onto the existing, already-tested business
 * logic (assignment/reassignment/review/reschedule/cancel/completion) - none of that logic itself
 * is touched, so existing workflow test coverage elsewhere in this suite is the real proof "no
 * regression" holds; this file only proves the notification side effect itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationSystemTest {

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
    @Autowired
    NotificationRepository notificationRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String PUBLICATION_TARGET_ID = "01926e3e-000a-7000-8000-000000000001";
    private static final String TEST_PASSWORD = "Passw0rd!";

    private record TestUser(String id, String email) {
    }

    // ------------------------------------------------------------------ fixture helpers

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private TestApiClient loginAs(TestUser user) throws Exception {
        TestApiClient client = new TestApiClient(port);
        client.login(user.email(), TEST_PASSWORD);
        return client;
    }

    private TestUser createUser(TestApiClient ceo, String fullName, String businessRoleId, long unique) throws Exception {
        String email = "notif-" + fullName.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + unique + "@kcpcbandhani.local";
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"" + TEST_PASSWORD + "\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"notification system regression test\"}");
        String userId = response.get("userId").asText();
        String permission = switch (businessRoleId) {
            case CAMERA_PERSON_ROLE_ID -> "PERM_18_SHOOT_EXECUTION";
            case VIDEO_EDITOR_ROLE_ID -> "PERM_19_EDIT_EXECUTION";
            case PUBLISHER_ROLE_ID -> "PERM_08_PUBLISHING_EXECUTION";
            default -> null;
        };
        if (permission != null) {
            grant(ceo, userId, permission);
        }
        return new TestUser(userId, email);
    }

    private void grant(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        HttpResponse<String> resp = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"notification system regression test grant\"}");
        if (resp.statusCode() != 201) {
            throw new IllegalStateException("Grant failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    private String approveToShootAssigned(TestApiClient ceo, String title, List<TestUser> camerapersons,
                                           List<TestUser> models, TestUser publisher) throws Exception {
        StringBuilder camJson = new StringBuilder();
        for (int i = 0; i < camerapersons.size(); i++) {
            if (i > 0) camJson.append(',');
            camJson.append('"').append(camerapersons.get(i).id()).append('"');
        }
        StringBuilder modelJson = new StringBuilder();
        for (int i = 0; i < models.size(); i++) {
            if (i > 0) modelJson.append(',');
            modelJson.append('"').append(models.get(i).id()).append('"');
        }
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/notif-" + Instant.now().toEpochMilli() + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\",\"publicationTargetIds\":[\"" + PUBLICATION_TARGET_ID + "\"]}],"
                        + "\"camerapersonUserIds\":[" + camJson + "],\"talentUserIds\":[" + modelJson + "],"
                        + "\"publisherUserIds\":[\"" + publisher.id() + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");
        Idea ideaEntity = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(ideaEntity).orElseThrow();
        return plan.getId().toString();
    }

    private void completeShoot(TestApiClient ceo, String planId, TestUser cam, TestUser editor) throws Exception {
        TestApiClient camClient = loginAs(cam);
        assertThat(camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode()).isEqualTo(200);
        assertThat(camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "").statusCode()).isEqualTo(200);
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam.id() + "\"],"
                        + "\"editorUserIds\":[\"" + editor.id() + "\"],\"leadEditorUserId\":\"" + editor.id() + "\"}");
        assertThat(approved.get("status").asText()).isEqualTo("EA");
    }

    private List<Notification> notificationsFor(TestUser user) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(
                userRepositoryLoad(user));
    }

    // Test-only convenience: NotificationRepository queries need a real User entity, not just an
    // id - resolved via the recipient's own notifications through a throwaway JPA lookup avoided
    // by instead filtering findAll() would be wasteful; simplest is a tiny repository-free lookup
    // through the API's own admin user list is overkill too - just re-login and use the principal
    // is unnecessary for a read-only repository call, so resolve via ContentPlanRepository's
    // shared EntityManager is unavailable here; instead we accept a UUID and let JPQL resolve it.
    private com.kcpc.mkt.identity.domain.User userRepositoryLoad(TestUser user) {
        return userRepository.findById(UUID.fromString(user.id())).orElseThrow();
    }

    @Autowired
    com.kcpc.mkt.identity.repository.UserRepository userRepository;

    // ------------------------------------------------------------------ 1: assignment creates notification

    @Test
    void newAssignmentCreatesNotificationForTheAssignee() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Assign Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser pub = createUser(ceo, "Assign Pub " + unique, PUBLISHER_ROLE_ID, unique + 1);

        String planId = approveToShootAssigned(ceo, "Notif Assign " + unique, List.of(cam), List.of(), pub);
        String contentId = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow().getContentId();

        List<Notification> notes = notificationsFor(cam);
        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).getType()).isEqualTo(NotificationType.TASK_ASSIGNED);
        assertThat(notes.get(0).getMessage()).contains(contentId);
        assertThat(notes.get(0).isUnread()).isTrue();
        assertThat(notes.get(0).getContentPlan().getId().toString()).isEqualTo(planId);
    }

    // ------------------------------------------------------------------ 2: multiple assignees each receive their own

    @Test
    void multipleAssigneesEachReceiveTheirOwnNotification() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam1 = createUser(ceo, "Multi Cam1 " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser cam2 = createUser(ceo, "Multi Cam2 " + unique, CAMERA_PERSON_ROLE_ID, unique + 1);
        TestUser model1 = createUser(ceo, "Multi Model1 " + unique, MODEL_ROLE_ID, unique + 2);
        TestUser model2 = createUser(ceo, "Multi Model2 " + unique, MODEL_ROLE_ID, unique + 3);
        TestUser pub = createUser(ceo, "Multi Pub " + unique, PUBLISHER_ROLE_ID, unique + 4);

        approveToShootAssigned(ceo, "Notif Multi " + unique, List.of(cam1, cam2), List.of(model1, model2), pub);

        assertThat(notificationsFor(cam1)).hasSize(1);
        assertThat(notificationsFor(cam2)).hasSize(1);
        assertThat(notificationsFor(model1)).hasSize(1);
        assertThat(notificationsFor(model2)).hasSize(1);
        assertThat(notificationsFor(pub)).hasSize(1);
    }

    // ------------------------------------------------------------------ 3: idempotent re-assignment does not duplicate

    @Test
    void idempotentPublisherAssignmentDoesNotDuplicateTheNotification() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Idem Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser editor = createUser(ceo, "Idem Ed " + unique, VIDEO_EDITOR_ROLE_ID, unique + 1);
        TestUser planningPub = createUser(ceo, "Idem PlanPub " + unique, PUBLISHER_ROLE_ID, unique + 2);
        TestUser finalPub = createUser(ceo, "Idem FinalPub " + unique, PUBLISHER_ROLE_ID, unique + 3);

        String planId = approveToShootAssigned(ceo, "Notif Idem " + unique, List.of(cam), List.of(), planningPub);
        completeShoot(ceo, planId, cam, editor);
        TestApiClient editorClient = loginAs(editor);
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor.id() + "\"],"
                        + "\"publisherUserIds\":[\"" + finalPub.id() + "\"]}");
        assertThat(notificationsFor(finalPub)).hasSize(1);

        // Re-assign the SAME Publisher again via the direct assignment API - PublishingService
        // #assignPublisher is idempotent (returns the already-existing active row, same id), so
        // this must NOT add a second TASK_ASSIGNED notification.
        HttpResponse<String> again = ceo.post("/api/v1/content-plans/" + planId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + finalPub.id() + "\"}");
        assertThat(again.statusCode()).isEqualTo(200);
        assertThat(notificationsFor(finalPub)).hasSize(1);
    }

    // ------------------------------------------------------------------ 4: wrong employees do not receive it

    @Test
    void unrelatedEmployeesDoNotReceiveTheNotification() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Scope Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser unrelatedCam = createUser(ceo, "Scope Unrelated " + unique, CAMERA_PERSON_ROLE_ID, unique + 1);
        TestUser pub = createUser(ceo, "Scope Pub " + unique, PUBLISHER_ROLE_ID, unique + 2);

        approveToShootAssigned(ceo, "Notif Scope " + unique, List.of(cam), List.of(), pub);

        assertThat(notificationsFor(cam)).hasSize(1);
        assertThat(notificationsFor(unrelatedCam)).isEmpty();
    }

    // ------------------------------------------------------------------ 5/6: review-required goes to the correct reviewer only

    @Test
    void reviewRequiredNotifiesOnlyExplicitPerm05GrantHoldersNotEveryone() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "RevReq Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser reviewer = createUser(ceo, "RevReq Reviewer " + unique, CAMERA_PERSON_ROLE_ID, unique + 1);
        grant(ceo, reviewer.id(), "PERM_05_SHOOT_REVIEW");
        TestUser bystander = createUser(ceo, "RevReq Bystander " + unique, CAMERA_PERSON_ROLE_ID, unique + 2);
        TestUser pub = createUser(ceo, "RevReq Pub " + unique, PUBLISHER_ROLE_ID, unique + 3);

        String planId = approveToShootAssigned(ceo, "Notif RevReq " + unique, List.of(cam), List.of(), pub);
        TestApiClient camClient = loginAs(cam);
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");

        List<Notification> reviewerNotes = notificationsFor(reviewer);
        assertThat(reviewerNotes).hasSize(1);
        assertThat(reviewerNotes.get(0).getType()).isEqualTo(NotificationType.REVIEW_REQUIRED);
        assertThat(notificationsFor(bystander)).isEmpty();
    }

    // ------------------------------------------------------------------ 5: stage transition creates only the appropriate notification (approve -> REVIEW_APPROVED, not CHANGES_REQUIRED)

    @Test
    void shootApprovalCreatesOnlyReviewApprovedNeverChangesRequired() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Approve Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser editor = createUser(ceo, "Approve Ed " + unique, VIDEO_EDITOR_ROLE_ID, unique + 1);
        TestUser pub = createUser(ceo, "Approve Pub " + unique, PUBLISHER_ROLE_ID, unique + 2);

        String planId = approveToShootAssigned(ceo, "Notif Approve " + unique, List.of(cam), List.of(), pub);
        completeShoot(ceo, planId, cam, editor);

        List<Notification> camNotes = notificationsFor(cam);
        // TASK_ASSIGNED (initial) + REVIEW_APPROVED (decision) - never CHANGES_REQUIRED.
        assertThat(camNotes).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.TASK_ASSIGNED, NotificationType.REVIEW_APPROVED);
        assertThat(camNotes).noneMatch(n -> n.getType() == NotificationType.CHANGES_REQUIRED);
        // The newly folded-in Editor gets their own TASK_ASSIGNED.
        assertThat(notificationsFor(editor)).extracting(Notification::getType)
                .containsExactly(NotificationType.TASK_ASSIGNED);
    }

    @Test
    void shootReworkCreatesChangesRequiredNeverReviewApproved() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Rework Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser reviewer = createUser(ceo, "Rework Reviewer " + unique, CAMERA_PERSON_ROLE_ID, unique + 1);
        grant(ceo, reviewer.id(), "PERM_05_SHOOT_REVIEW");
        TestUser pub = createUser(ceo, "Rework Pub " + unique, PUBLISHER_ROLE_ID, unique + 2);

        String planId = approveToShootAssigned(ceo, "Notif Rework " + unique, List.of(cam), List.of(), pub);
        TestApiClient camClient = loginAs(cam);
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        JsonNode rejected = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":false,\"reason\":\"needs another take\"}");
        assertThat(rejected.get("status").asText()).isEqualTo("SIP");

        List<Notification> camNotes = notificationsFor(cam);
        assertThat(camNotes).extracting(Notification::getType)
                .contains(NotificationType.CHANGES_REQUIRED);
        assertThat(camNotes).noneMatch(n -> n.getType() == NotificationType.REVIEW_APPROVED);
    }

    // ------------------------------------------------------------------ 7: publishing-ready notifies the Publisher(s)

    @Test
    void publishingReadyNotifiesThePublisher() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "PubReady Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser editor = createUser(ceo, "PubReady Ed " + unique, VIDEO_EDITOR_ROLE_ID, unique + 1);
        TestUser planningPub = createUser(ceo, "PubReady PlanPub " + unique, PUBLISHER_ROLE_ID, unique + 2);
        TestUser finalPub = createUser(ceo, "PubReady FinalPub " + unique, PUBLISHER_ROLE_ID, unique + 3);

        String planId = approveToShootAssigned(ceo, "Notif PubReady " + unique, List.of(cam), List.of(), planningPub);
        completeShoot(ceo, planId, cam, editor);

        TestApiClient editorClient = loginAs(editor);
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor.id() + "\"],"
                        + "\"publisherUserIds\":[\"" + finalPub.id() + "\"]}");
        assertThat(approved.get("status").asText()).isEqualTo("RFP");

        List<Notification> finalPubNotes = notificationsFor(finalPub);
        assertThat(finalPubNotes).hasSize(1);
        assertThat(finalPubNotes.get(0).getType()).isEqualTo(NotificationType.TASK_ASSIGNED);
        assertThat(finalPubNotes.get(0).getTitle()).isEqualTo("Publishing Ready");
    }

    // ------------------------------------------------------------------ 9: reschedule notifies affected users

    @Test
    void rescheduleNotifiesEveryCurrentlyActiveAssignee() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Resched Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser model = createUser(ceo, "Resched Model " + unique, MODEL_ROLE_ID, unique + 1);
        TestUser pub = createUser(ceo, "Resched Pub " + unique, PUBLISHER_ROLE_ID, unique + 2);

        String planId = approveToShootAssigned(ceo, "Notif Resched " + unique, List.of(cam), List.of(model), pub);
        HttpResponse<String> resp = ceo.post("/api/v1/content-plans/" + planId + "/reschedule",
                "{\"stageContext\":\"SHOOTING\",\"newShootDate\":\"" + LocalDate.now().plusDays(6)
                        + "\",\"reason\":\"studio unavailable\"}");
        assertThat(resp.statusCode()).isEqualTo(200);

        // Rahul-overlap case (Model + Cameraperson being the same real person) is exercised
        // separately by AdminActionService's own currentlyAffectedUsers dedup logic - here we just
        // confirm each distinct real person gets exactly one TASK_RESCHEDULED alongside their
        // earlier TASK_ASSIGNED.
        assertThat(notificationsFor(cam)).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.TASK_ASSIGNED, NotificationType.TASK_RESCHEDULED);
        assertThat(notificationsFor(model)).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.TASK_ASSIGNED, NotificationType.TASK_RESCHEDULED);
        assertThat(notificationsFor(pub)).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.TASK_ASSIGNED, NotificationType.TASK_RESCHEDULED);
    }

    // ------------------------------------------------------------------ 10: cancel notifies affected users

    @Test
    void cancelNotifiesEveryCurrentlyActiveAssignee() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Cancel Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser pub = createUser(ceo, "Cancel Pub " + unique, PUBLISHER_ROLE_ID, unique + 1);

        String planId = approveToShootAssigned(ceo, "Notif Cancel " + unique, List.of(cam), List.of(), pub);
        HttpResponse<String> resp = ceo.post("/api/v1/content-plans/" + planId + "/cancel",
                "{\"reason\":\"idea scrapped\"}");
        assertThat(resp.statusCode()).isEqualTo(200);

        assertThat(notificationsFor(cam)).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.TASK_ASSIGNED, NotificationType.TASK_CANCELLED);
        List<Notification> cancelNote = notificationsFor(cam).stream()
                .filter(n -> n.getType() == NotificationType.TASK_CANCELLED).toList();
        assertThat(cancelNote).hasSize(1);
        assertThat(cancelNote.get(0).getMessage()).contains("has been cancelled");
    }

    // ------------------------------------------------------------------ reassignment: new + previous sides

    @Test
    void reassignmentNotifiesTheNewAssigneeAndThePreviousAssigneeSeparately() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser originalCam = createUser(ceo, "Reassign Orig " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser newCam = createUser(ceo, "Reassign New " + unique, CAMERA_PERSON_ROLE_ID, unique + 1);
        TestUser pub = createUser(ceo, "Reassign Pub " + unique, PUBLISHER_ROLE_ID, unique + 2);

        String planId = approveToShootAssigned(ceo, "Notif Reassign " + unique, List.of(originalCam), List.of(), pub);
        HttpResponse<String> resp = ceo.post("/api/v1/content-plans/" + planId + "/reassign",
                "{\"taskStage\":\"SHOOTING\",\"newAssigneeUserIds\":[\"" + newCam.id() + "\"],\"reason\":\"schedule conflict\"}");
        assertThat(resp.statusCode()).isEqualTo(200);

        List<Notification> origNotes = notificationsFor(originalCam);
        assertThat(origNotes).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.TASK_ASSIGNED, NotificationType.TASK_REASSIGNED_AWAY);
        List<Notification> newNotes = notificationsFor(newCam);
        assertThat(newNotes).hasSize(1);
        assertThat(newNotes.get(0).getType()).isEqualTo(NotificationType.TASK_REASSIGNED);
        assertThat(newNotes.get(0).getMessage()).contains("has been assigned to you");
    }

    // ------------------------------------------------------------------ 11/12/13: unread count, mark one read, mark all read

    @Test
    void unreadCountAndMarkReadAndMarkAllReadWorkCorrectly() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Unread Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser pub = createUser(ceo, "Unread Pub " + unique, PUBLISHER_ROLE_ID, unique + 1);

        approveToShootAssigned(ceo, "Notif Unread A " + unique, List.of(cam), List.of(), pub);
        String plan2 = approveToShootAssigned(ceo, "Notif Unread B " + unique, List.of(cam), List.of(), pub);

        TestApiClient camClient = loginAs(cam);
        String body = camClient.get("/app/my-work").body();
        assertThat(body).contains("class=\"app-header-notification-badge\">2</span>");

        List<Notification> notes = notificationsFor(cam);
        assertThat(notes).hasSize(2);

        // Mark one read.
        HttpResponse<String> markOne = camClient.post(
                "/app/notifications/" + notes.get(0).getId() + "/read", "");
        assertThat(markOne.statusCode()).isIn(200, 302, 303);
        long unreadAfterOne = notificationRepository.countByRecipientAndReadAtIsNull(userRepositoryLoad(cam));
        assertThat(unreadAfterOne).isEqualTo(1);

        // Mark all read.
        HttpResponse<String> markAll = camClient.post("/app/notifications/mark-all-read", "");
        assertThat(markAll.statusCode()).isIn(200, 302, 303);
        long unreadAfterAll = notificationRepository.countByRecipientAndReadAtIsNull(userRepositoryLoad(cam));
        assertThat(unreadAfterAll).isEqualTo(0);

        String bodyAfter = camClient.get("/app/my-work").body();
        assertThat(bodyAfter).doesNotContain("app-header-notification-badge");
    }

    // ------------------------------------------------------------------ 14: cross-user access denied

    @Test
    void userCannotMarkAnotherUsersNotificationAsRead() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Cross Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser intruder = createUser(ceo, "Cross Intruder " + unique, CAMERA_PERSON_ROLE_ID, unique + 1);
        TestUser pub = createUser(ceo, "Cross Pub " + unique, PUBLISHER_ROLE_ID, unique + 2);

        approveToShootAssigned(ceo, "Notif Cross " + unique, List.of(cam), List.of(), pub);
        Notification camNote = notificationsFor(cam).get(0);

        TestApiClient intruderClient = loginAs(intruder);
        HttpResponse<String> resp = intruderClient.postFormAjax(
                "/app/notifications/" + camNote.getId() + "/read", java.util.Map.of());
        assertThat(resp.statusCode()).isEqualTo(403);

        // Confirm it's genuinely still unread - the forbidden attempt had no side effect.
        Notification reloaded = notificationRepository.findById(camNote.getId()).orElseThrow();
        assertThat(reloaded.isUnread()).isTrue();

        // A user's own "View all" list never includes another user's notifications either.
        String intruderListBody = intruderClient.get("/app/notifications").body();
        assertThat(intruderListBody).doesNotContain(camNote.getMessage());
    }

    // ------------------------------------------------------------------ 15: click-through link is the existing Content Detail route

    @Test
    void notificationClickThroughLinksToTheExistingContentDetailRoute() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Link Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser pub = createUser(ceo, "Link Pub " + unique, PUBLISHER_ROLE_ID, unique + 1);

        String planId = approveToShootAssigned(ceo, "Notif Link " + unique, List.of(cam), List.of(), pub);

        TestApiClient camClient = loginAs(cam);
        String body = camClient.get("/app/my-work").body();
        assertThat(body).contains("href=\"/app/deliverables/" + planId + "\"");

        // The link genuinely resolves to the real, already-existing Content Detail page.
        assertThat(camClient.get("/app/deliverables/" + planId).statusCode()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ TASK_COMPLETED notifies the plan's Preparer

    @Test
    void deliverableCompletionNotifiesThePlanPreparer() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Complete Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser editor = createUser(ceo, "Complete Ed " + unique, VIDEO_EDITOR_ROLE_ID, unique + 1);
        TestUser pub = createUser(ceo, "Complete Pub " + unique, PUBLISHER_ROLE_ID, unique + 2);

        String planId = approveToShootAssigned(ceo, "Notif Complete " + unique, List.of(cam), List.of(), pub);
        completeShoot(ceo, planId, cam, editor);
        TestApiClient editorClient = loginAs(editor);
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor.id() + "\"],"
                        + "\"publisherUserIds\":[\"" + pub.id() + "\"]}");

        TestApiClient pubClient = loginAs(pub);
        pubClient.post("/api/v1/content-plans/" + planId + "/publishing/start", "");
        String outputId = plannedOutputRepository.findByContentPlan(
                        contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow())
                .stream().findFirst().map(PlannedOutput::getId).map(UUID::toString).orElseThrow();
        String pastTimestamp = Instant.now().minus(3, ChronoUnit.DAYS).toString();
        pubClient.postJson("/api/v1/content-plans/" + planId + "/publishing/events",
                "{\"plannedOutputId\":\"" + outputId + "\",\"publicationTargetId\":\"" + PUBLICATION_TARGET_ID
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/notif-complete-" + unique + "\"}");
        String obligationId = obligationRepository.findByEvent_ContentPlan_Id(UUID.fromString(planId)).stream()
                .findFirst().map(o -> o.getId().toString()).orElseThrow();
        ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/draft",
                "{\"hookRatePercent\":80.00,\"hookRateIsNa\":false,\"holdRateIsNa\":true,"
                        + "\"views\":5000,\"avgViewDurationIsNa\":true}");
        JsonNode submitted = ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/submit", "");
        assertThat(submitted.get("submitted").asBoolean()).isTrue();
        JsonNode finalPlan = ceo.getJson("/api/v1/content-plans/" + planId);
        assertThat(finalPlan.get("status").asText()).isEqualTo("COMP");

        // CEO is the reviewer/preparer for this fixture (approveToShootAssigned's own "reviewer"
        // is the CEO caller - PlanningPreparer#getPreparedBy mirrors that).
        List<Notification> ceoCompletedNotes = notificationRepository.findByRecipientOrderByCreatedAtDesc(
                        userRepository.findByEmailIgnoreCase("ceo@kcpcbandhani.local").orElseThrow()).stream()
                .filter(n -> n.getType() == NotificationType.TASK_COMPLETED
                        && n.getContentPlan() != null && n.getContentPlan().getId().toString().equals(planId))
                .toList();
        assertThat(ceoCompletedNotes).hasSize(1);
    }
}
