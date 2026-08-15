package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.identity.dto.ChangeBusinessRoleRequest;
import com.kcpc.mkt.identity.dto.CreateUserRequest;
import com.kcpc.mkt.identity.dto.ReasonRequest;
import com.kcpc.mkt.identity.dto.UserProfileResponse;
import com.kcpc.mkt.identity.service.UserAdminService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** BRS-REQ-003..005: exclusive CEO user administration. */
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminRestController {

    private final UserAdminService userAdminService;

    public UserAdminRestController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @PostMapping
    public ResponseEntity<UserProfileResponse> create(@Valid @RequestBody CreateUserRequest request,
                                                        @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var user = userAdminService.createUser(principal.user(), request.fullName(), request.email(),
                request.password(), request.businessRoleId(), request.creationReason());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserProfileResponse.from(user));
    }

    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<UserProfileResponse> deactivate(@PathVariable UUID userId,
                                                            @Valid @RequestBody ReasonRequest request,
                                                            @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var user = userAdminService.deactivate(principal.user(), userId, request.reason());
        return ResponseEntity.ok(UserProfileResponse.from(user));
    }

    @PostMapping("/{userId}/activate")
    public ResponseEntity<UserProfileResponse> activate(@PathVariable UUID userId,
                                                          @Valid @RequestBody ReasonRequest request,
                                                          @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var user = userAdminService.activate(principal.user(), userId, request.reason());
        return ResponseEntity.ok(UserProfileResponse.from(user));
    }

    @PostMapping("/{userId}/business-role")
    public ResponseEntity<UserProfileResponse> changeBusinessRole(@PathVariable UUID userId,
                                                                    @Valid @RequestBody ChangeBusinessRoleRequest request,
                                                                    @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var user = userAdminService.changeBusinessRole(principal.user(), userId, request.businessRoleId(), request.reason());
        return ResponseEntity.ok(UserProfileResponse.from(user));
    }
}
