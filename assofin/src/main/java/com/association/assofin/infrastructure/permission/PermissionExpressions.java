package com.association.assofin.infrastructure.permission;

import com.association.assofin.role.permissions.RolePermissions;
import com.association.assofin.user.permissions.UserPermissions;
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

    public String userCreate() {
        return UserPermissions.CREATE.value();
    }

    public String userRead() {
        return UserPermissions.READ.value();
    }

    public String userUpdate() {
        return UserPermissions.UPDATE.value();
    }

    public String userDelete() {
        return UserPermissions.DELETE.value();
    }
}

