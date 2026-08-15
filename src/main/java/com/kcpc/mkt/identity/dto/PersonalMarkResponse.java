package com.kcpc.mkt.identity.dto;

import com.kcpc.mkt.marks.domain.PersonalMarkAttribution;
import com.kcpc.mkt.marks.domain.RoleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** BRS-REQ-066..070: strict Mark privacy - only ever constructed for the recipient's own attributions. */
public record PersonalMarkResponse(UUID attributionId, String contentId, RoleType roleType, BigDecimal markValue,
                                    Instant attributedAt) {
    public static PersonalMarkResponse from(PersonalMarkAttribution attribution) {
        return new PersonalMarkResponse(attribution.getId(), attribution.getContentPlan().getContentId(),
                attribution.getRoleType(), attribution.getAttributedMarkValue(), attribution.getAttributedAt());
    }
}
