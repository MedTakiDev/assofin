package com.association.assofin.infrastructure.permission;

import com.association.assofin.permission.PermissionDefinition;
import com.association.assofin.products.ProductPermissions;
import com.association.assofin.role.permissions.RolePermissions;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Component
public class PermissionRegistry {

    public List<PermissionDefinition> all() {
        return Stream.of(
                        RolePermissions.values(),
                        ProductPermissions.values() // only as prototype add only required domains permission we should persist into the database
                        //UserPermission.values()
                )
                .flatMap(Arrays::stream)
                .map(permission -> (PermissionDefinition) permission)
                .toList();
    }
}