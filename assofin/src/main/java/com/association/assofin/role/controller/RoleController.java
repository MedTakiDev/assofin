package com.association.assofin.role.controller;

import com.association.assofin.role.entity.Role;
import com.association.assofin.role.dto.request.CreateRoleRequestDTO;
import com.association.assofin.role.dto.request.RolePageRequestDTO;
import com.association.assofin.role.service.RoleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
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

    @GetMapping("/")
    @ResponseStatus(HttpStatus.ACCEPTED)
    //@PreAuthorize("hasAuthority(@permissions.roleCreate())") athentication already handled by the jwt filter
    public ResponseEntity<?> getRoles(@RequestBody @Valid RolePageRequestDTO readRolesDTO) throws MessagingException {

        return ResponseEntity
                .accepted()
                .body(roleService.findRolesPage(readRolesDTO));
    }

    @PostMapping("/add")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority(@permissions.roleCreate())")
    public ResponseEntity<?> create(@RequestBody @Valid CreateRoleRequestDTO request) throws MessagingException {
        Role role = roleService.save(request);
        return ResponseEntity
                .accepted()
                .body(role);
    }



}
