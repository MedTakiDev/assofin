package com.association.assofin.role.controller;

import com.association.assofin.role.dto.request.UpdateRoleRequestDTO;
import com.association.assofin.role.dto.response.RolePageResponseDTO;
import com.association.assofin.role.dto.response.RoleResponseDTO;
import com.association.assofin.role.dto.request.CreateRoleRequestDTO;
import com.association.assofin.role.dto.request.RolePageRequestDTO;
import com.association.assofin.role.service.RoleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("role")
@RequiredArgsConstructor
@Tag(name = "role")
public class RoleController {
    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority(@permissions.roleRead())")
    public ResponseEntity<RolePageResponseDTO> getRoles(@Valid @NotNull RolePageRequestDTO request) {
        return ResponseEntity.ok(roleService.findRolesPage(request));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(@permissions.roleCreate())")
    public ResponseEntity<RoleResponseDTO> create(@RequestBody @Valid CreateRoleRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.save(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority(@permissions.roleUpdate())")
    public ResponseEntity<RoleResponseDTO> updateRole(
            @PathVariable Long id,
            @RequestBody @Valid UpdateRoleRequestDTO request
    ) {
        return ResponseEntity.ok(roleService.updateRole(id, request));
    }

}
