package com.calyvora.access;

import com.calyvora.access.dto.RoleDtos.AssignRoleRequest;
import com.calyvora.access.dto.RoleDtos.CreateRoleRequest;
import com.calyvora.access.dto.RoleDtos.PermissionInfo;
import com.calyvora.access.dto.RoleDtos.RoleResponse;
import com.calyvora.access.dto.RoleDtos.UpdateRoleRequest;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Roles and permissions for one company (PD-54). Managing them is itself a permission. */
@RestController
@PreAuthorize("@perm.has('MEMBERS_MANAGE')")
public class RoleController {

    private final RoleService roles;

    public RoleController(RoleService roles) {
        this.roles = roles;
    }

    @GetMapping("/api/v1/roles/permissions")
    public List<PermissionInfo> catalogue() {
        return roles.catalogue();
    }

    @GetMapping("/api/v1/roles")
    public List<RoleResponse> list() {
        return roles.list();
    }

    @PostMapping("/api/v1/roles")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roles.create(req));
    }

    @PatchMapping("/api/v1/roles/{id}")
    public RoleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest req) {
        return roles.update(id, req);
    }

    @DeleteMapping("/api/v1/roles/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        roles.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/v1/company/members/{userId}/role")
    public ResponseEntity<Void> assign(@PathVariable UUID userId, @Valid @RequestBody AssignRoleRequest req,
                                       @CurrentUser AuthPrincipal caller) {
        roles.assign(userId, req, caller);
        return ResponseEntity.noContent().build();
    }
}
