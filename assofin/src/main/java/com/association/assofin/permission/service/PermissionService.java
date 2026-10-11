package com.association.assofin.permission.service;

import com.association.assofin.permission.repository.PermissionRepository;
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
