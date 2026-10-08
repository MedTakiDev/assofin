package com.association.assofin.permission;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final PermissionRepository permissionRepository;

    public Boolean existsByName(String name){
        return permissionRepository.existsByName(name);
    }
}
