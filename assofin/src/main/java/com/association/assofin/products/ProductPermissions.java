package com.association.assofin.products;

import com.association.assofin.permission.entity.PermissionDefinition;

//only for test
public enum ProductPermissions implements PermissionDefinition {
    CREATE("product::create"),
    READ("product::read"),
    UPDATE("product::update"),
    DELETE("product::delete");

    private final String value;

    ProductPermissions(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
