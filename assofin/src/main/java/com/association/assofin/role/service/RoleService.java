package com.association.assofin.role.service;

import com.association.assofin.permission.entity.Permission;
import com.association.assofin.permission.service.PermissionService;
import com.association.assofin.role.dto.mapper.RoleMapper;
import com.association.assofin.role.dto.request.CreateRoleRequestDTO;
import com.association.assofin.role.dto.request.RolePageRequestDTO;
import com.association.assofin.role.dto.request.UpdateRoleRequestDTO;
import com.association.assofin.role.dto.response.RolePageResponseDTO;
import com.association.assofin.role.dto.response.RoleResponseDTO;
import com.association.assofin.role.entity.Role;
import com.association.assofin.role.repository.RoleRepository;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionService permissionService;
    private final RoleMapper roleMapper;

    @Transactional(readOnly = true)
    public Role findByName(String name) {
        return roleRepository.findByName(name).orElseThrow(() -> new EntityNotFoundException("Role with name " + name + " not found"));
    }

    @Transactional(readOnly = true)
    public Role findById(long id) {
        return roleRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Role with id: " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public RolePageResponseDTO findRolesPage(RolePageRequestDTO request) {
        Pageable pageable = PageRequest.of(request.page(), request.size(), Sort.by("id"));
        return roleMapper.toPageDto(roleRepository.findAll(pageable));   // mapper runs inside the tx
    }


    @Transactional
    public RoleResponseDTO save(CreateRoleRequestDTO request) {

        if (roleRepository.existsByName(request.name().trim())) {
            throw new EntityExistsException("Role with name " + request.name() + " already exists");
        }

        Role role = Role.builder().name(request.name().trim()).build();
        role.replacePermissions(resolvePermissions(request.permissions()));

        return roleMapper.toDto(roleRepository.save(role));
    }


    @Transactional
    public RoleResponseDTO updateRole(Long id, UpdateRoleRequestDTO request) {
        Role role = roleRepository.findByIdWithRolePermissions(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found with ID: " + id));

        if (request.name() != null && !request.name().isBlank()) {
            String newName = request.name().trim();
            if (roleRepository.existsByNameAndIdNot(newName, id)) {
                throw new EntityExistsException("Role with name " + newName + " already exists");
            }
            role.setName(newName);
        }

        if (request.permissions() != null) {
            role.replacePermissions(resolvePermissions(request.permissions()));
        }

        return roleMapper.toDto(role);   // managed entity, flushed at commit
    }

    private Set<Permission> resolvePermissions(Set<String> requestedNames) {
        if (requestedNames.isEmpty()) {
            return Set.of();
        }
        Set<Permission> validPermissions = permissionService.findByNameIn(requestedNames);
        if (validPermissions.size() != requestedNames.size()) {
            Set<String> missing = new HashSet<>(requestedNames);
            validPermissions.forEach(validPermission -> missing.remove(validPermission.getName()));
            throw new EntityNotFoundException("The following permissions do not exist: " + missing);
        }
        return validPermissions;
    }
}
