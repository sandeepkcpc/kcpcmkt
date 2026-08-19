package com.kcpc.mkt.planning.dto;

import java.util.UUID;

/**
 * ENG-067: a Models/Talent entry to write - {@code talentUserId} is null for the frozen REST
 * contract's plain-name-string path (API-OP-017/018), populated for the MVC Model(s) picker path
 * (which only ever offers real Model-Business-Role users). Not rendered by any JSP, so a record
 * is fine here (ENG-031's getter-only constraint applies only to JSP-EL-bound view models).
 */
public record TalentSelection(String talentName, UUID talentUserId) {
}
