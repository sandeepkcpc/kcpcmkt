package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.identity.dto.GrantPermissionRequest;
import com.kcpc.mkt.identity.dto.ModifyGrantRequest;
import com.kcpc.mkt.identity.dto.ReasonRequest;
import com.kcpc.mkt.identity.service.PermissionGrantAdminService;
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

/** BRS-REQ-006..013 / API-OP-065: exclusive CEO Operational Permission grant lifecycle. */
@RestController
@RequestMapping("/api/v1/admin/permission-grants")
public class PermissionGrantAdminRestController {

    private final PermissionGrantAdminService permissionGrantAdminService;

    public PermissionGrantAdminRestController(PermissionGrantAdminService permissionGrantAdminService) {
        this.permissionGrantAdminService = permissionGrantAdminService;
    }

    @PostMapping
    public ResponseEntity<Void> grant(@Valid @RequestBody GrantPermissionRequest request,
                                       @AuthenticationPrincipal KcpcUserPrincipal principal) {
        permissionGrantAdminService.grant(principal.user(), request.granteeUserId(), request.permission(),
                request.scopeType(), request.stages(), request.workflowInstanceIds(), request.effectiveFrom(),
                request.effectiveUntil(), request.reason());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{id}/modify")
    public ResponseEntity<Void> modify(@PathVariable UUID id, @Valid @RequestBody ModifyGrantRequest request,
                                        @AuthenticationPrincipal KcpcUserPrincipal principal) {
        permissionGrantAdminService.modifyExpiry(principal.user(), id, request.newEffectiveUntil(), request.reason());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request,
                                        @AuthenticationPrincipal KcpcUserPrincipal principal) {
        permissionGrantAdminService.revoke(principal.user(), id, request.reason());
        return ResponseEntity.ok().build();
    }
}
