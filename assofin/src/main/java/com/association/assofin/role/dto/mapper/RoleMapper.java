package com.association.assofin.role.dto.mapper;

import com.association.assofin.role.dto.response.RolePageResponseDTO;
import com.association.assofin.role.dto.response.RoleResponseDTO;
import com.association.assofin.role.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class RoleMapper {

    public RoleResponseDTO toDto(
            Role role,
            Set<String> permissions
    ) {
        return new RoleResponseDTO(
                role.getId(),
                role.getName(),
                permissions
        );
    }

    public RolePageResponseDTO toPageDto(
            Page<Role> rolePage,
            List<RoleResponseDTO> content
    ) {
        return new RolePageResponseDTO(
                content,
                rolePage.getNumber(),
                rolePage.getSize(),
                rolePage.getTotalElements()
        );
    }
}
