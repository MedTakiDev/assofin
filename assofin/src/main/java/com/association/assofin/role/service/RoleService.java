package com.association.assofin.role.service;

import com.association.assofin.permission.service.PermissionService;
import com.association.assofin.role.dto.mapper.RoleMapper;
import com.association.assofin.role.dto.response.RoleResponseDTO;
import com.association.assofin.role.entity.Role;
import com.association.assofin.role.repository.RolePermissionRepository;
import com.association.assofin.role.repository.RoleRepository;
import com.association.assofin.role.dto.request.CreateRoleRequestDTO;
import com.association.assofin.role.dto.request.RolePageRequestDTO;
import com.association.assofin.role.dto.response.RolePageResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionService permissionService;
    private final RolePermissionRepository rolePermissionRepository;
    private final RolePermissionService rolePermissionService;
    private final RoleMapper roleMapper;

    public Role findByName(String name) {
        return roleRepository.findByName(name).orElseThrow(()-> new IllegalStateException("Role with name " + name + " not found"));
    }


    @Transactional
    public Role save(CreateRoleRequestDTO role) {
        role.permissions().forEach(permission -> {
           if (!permissionService.existsByName(permission)){
               throw new IllegalStateException("Permission with name " + permission + " not found");
           }
        });
        if (roleRepository.existsByName(role.name())) {
            throw new IllegalStateException("Role with name " + role.name() + " already exists");
        }
        return roleRepository.save(Role.builder()
                .name(role.name())
                .build());
    }


    @Transactional(readOnly = true)
    public RolePageResponseDTO findRolesPage(RolePageRequestDTO request) {
        Pageable pageable = PageRequest.of(
                request.page(),
                request.size(),
                Sort.by(
                        Sort.Order.asc("name"),
                        Sort.Order.asc("id")
                )
        );

        Page<Role> rolePage = roleRepository.findAll(pageable);

        List<Long> roleIds = rolePage.getContent()
                .stream()
                .map(Role::getId)
                .toList();

        Map<Long, Set<String>> permissionsByRoleId = rolePermissionService.findPermissionNamesByRoleIds(roleIds);

        List<RoleResponseDTO> content = rolePage.getContent()
                .stream()
                .map(roleInPage -> roleMapper.toDto(
                        roleInPage,
                        permissionsByRoleId.getOrDefault(
                                roleInPage.getId(),
                                Set.of()
                        )
                ))
                .toList();

        return roleMapper.toPageDto(rolePage, content);
    }
}
