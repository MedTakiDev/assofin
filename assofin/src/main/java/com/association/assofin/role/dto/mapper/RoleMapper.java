package com.association.assofin.role.dto.mapper;

import com.association.assofin.role.dto.response.RolePageResponseDTO;
import com.association.assofin.role.dto.response.RoleResponseDTO;
import com.association.assofin.role.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RoleMapper {

    // Single role: reads the lazy collection, so it must run inside the transaction
    public RoleResponseDTO toDto(Role role) {
        Set<String> permissions = role.getRolePermissions().stream()
                .map(rp -> rp.getPermission().getName())
                .collect(Collectors.toUnmodifiableSet());   // copy: DTO holds no Hibernate proxy

        return new RoleResponseDTO(role.getId(), role.getName(), permissions);
    }

    // Page: maps each role with the same method, so there is only one mapping path
    public RolePageResponseDTO toPageDto(Page<Role> rolesPage) {
        return new RolePageResponseDTO(
                rolesPage.getContent().stream().map(this::toDto).toList(),
                rolesPage.getNumber(),
                rolesPage.getSize(),
                rolesPage.getTotalElements());
    }
}
