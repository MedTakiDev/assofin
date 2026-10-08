package com.association.assofin.role;

import com.association.assofin.permission.PermissionService;
import com.association.assofin.role.dto.NewRoleRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionService permissionService;

    public Role findByName(String name) {
        return roleRepository.findByName(name).orElseThrow(()-> new IllegalStateException("Role with name " + name + " not found"));
    }


    @Transactional
    public Role save(NewRoleRequestDto role) {
        if(roleRepository.findByName(role.name()).isPresent()) {
            throw new IllegalStateException("Role with name " + role.name() + " already exists");
        }
        role.permissions().forEach(permission -> {
           if (!permissionService.existsByName(permission)){
               throw new IllegalStateException("Permission with name " + permission + " not found");
           }
        });
        return roleRepository.save(Role.builder()
                .name(role.name())
                .build());
    }
}
