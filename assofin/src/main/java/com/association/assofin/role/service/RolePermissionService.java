package com.association.assofin.role.service;

import com.association.assofin.role.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final RolePermissionRepository rolePermissionRepository;

    @Transactional(readOnly = true)
    public Map<Long, Set<String>> findPermissionNamesByRoleIds(
            Collection<Long> roleIds
    ) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }

        return rolePermissionRepository.findAllByRole_IdIn(roleIds)
                .stream()
                .collect(Collectors.groupingBy(
                        rp -> rp.getRole().getId(),
                        Collectors.mapping(
                                rp -> rp.getPermission().getName(),
                                Collectors.toSet()
                        )
                ));
    }
}
