package com.kcpc.mkt.production.dto;

import java.util.List;
import java.util.UUID;

/**
 * Shared by both Shoot Review and Edit Review decisions (ShootingRestController/EditingRestController).
 * {@code editorUserIds}/{@code leadEditorUserId} are only meaningful for Shoot Review Approve (folds in
 * Editor team assignment, incl. Editor Lead - see {@code ShootingService#decideShootReview});
 * {@code publisherUserIds} is only meaningful for Edit Review Approve (folds in Publisher team
 * assignment - see {@code EditingService#decideEditReview}) - Publisher assignment has no Lead
 * concept, unlike Editor/Cameraperson (explicit product decision - see ENG-036/ENG-044). Each
 * controller reads only its own fields.
 */
public record ReviewDecisionRequest(boolean approve, String reason, List<UUID> qualifyingRecipientUserIds,
                                     List<UUID> editorUserIds, UUID leadEditorUserId,
                                     List<UUID> publisherUserIds) {
}
