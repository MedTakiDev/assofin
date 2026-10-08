package com.association.assofin.role;

import com.association.assofin.auth.dto.RegistrationRequestDTO;
import com.association.assofin.role.dto.NewRoleRequestDto;
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

    @PostMapping("/add")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority(@permissions.roleCreate())")
    public ResponseEntity<?> create(@RequestBody @Valid NewRoleRequestDto request) throws MessagingException {
        Role role = roleService.save(request);
        return ResponseEntity
                .accepted()
                .body(role);
    }

}
