package com.association.assofin.user.permissions;

import com.association.assofin.permission.entity.PermissionDefinition;

public enum UserPermissions implements PermissionDefinition {

    CREATE("user::create"),
    READ("user::read"),
    UPDATE("user::update"),
    DELETE("user::delete");

    private final String value;

    UserPermissions(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
