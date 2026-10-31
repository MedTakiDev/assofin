package com.association.assofin.role.permissions;

import com.association.assofin.permission.entity.PermissionDefinition;

public enum RolePermissions implements PermissionDefinition {

    CREATE("role::create"),
    READ("role::read"),
    UPDATE("role::update"),
    DELETE("role::delete");

    private final String value;

    RolePermissions(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}