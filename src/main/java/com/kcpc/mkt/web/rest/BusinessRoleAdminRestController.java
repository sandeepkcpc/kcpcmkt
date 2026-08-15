package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.identity.domain.BusinessRole;
import com.kcpc.mkt.identity.dto.CreateBusinessRoleRequest;
import com.kcpc.mkt.identity.service.BusinessRoleAdminService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** BRS-REQ-085: expandable Business Role catalogue administration, CEO-exclusive. */
@RestController
@RequestMapping("/api/v1/admin/business-roles")
public class BusinessRoleAdminRestController {

    private final BusinessRoleAdminService businessRoleAdminService;

    public BusinessRoleAdminRestController(BusinessRoleAdminService businessRoleAdminService) {
        this.businessRoleAdminService = businessRoleAdminService;
    }

    @GetMapping
    public ResponseEntity<List<BusinessRole>> list() {
        return ResponseEntity.ok(businessRoleAdminService.listActive());
    }

    @PostMapping
    public ResponseEntity<BusinessRole> create(@Valid @RequestBody CreateBusinessRoleRequest request,
                                                @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var role = businessRoleAdminService.create(principal.user(), request.roleName(), request.accessClass());
        return ResponseEntity.status(HttpStatus.CREATED).body(role);
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<BusinessRole> deactivate(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal) {
        return ResponseEntity.ok(businessRoleAdminService.deactivate(principal.user(), id));
    }
}
