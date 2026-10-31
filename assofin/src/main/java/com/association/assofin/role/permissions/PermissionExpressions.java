package com.association.assofin.role.permissions;

import org.springframework.stereotype.Component;

@Component("permissions")
public final class PermissionExpressions {

    public String roleCreate() {
        return RolePermissions.CREATE.value();
    }

    public String roleRead() {
        return RolePermissions.READ.value();
    }

    public String roleUpdate() {
        return RolePermissions.UPDATE.value();
    }

    public String roleDelete() {
        return RolePermissions.DELETE.value();
    }
}

