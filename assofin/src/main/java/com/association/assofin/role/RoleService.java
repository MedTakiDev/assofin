package com.association.assofin.role;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public Role findByName(String name) {
        return roleRepository.findByName(name).orElseThrow(()-> new IllegalStateException("Role with name " + name + " not found"));
    }
}
