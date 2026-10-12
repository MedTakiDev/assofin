package com.association.assofin.permission.service;

import com.association.assofin.permission.entity.Permission;
import com.association.assofin.permission.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final PermissionRepository permissionRepository;

    public Boolean existsByName(String name){
        return permissionRepository.existsByName(name);
    }

    public Set<Permission> findByNameIn(Set<String> permissions) {
        return permissionRepository.findByNameIn(permissions);
    }
}
