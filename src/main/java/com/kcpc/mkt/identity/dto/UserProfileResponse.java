package com.kcpc.mkt.identity.dto;

import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.User;

import java.util.UUID;

public record UserProfileResponse(UUID userId, String fullName, String email, String businessRoleName,
                                   AccessClass accessClass) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getBusinessRole().getRoleName(), user.resolvedAccessClass());
    }
}
