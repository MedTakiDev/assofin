package com.association.assofin.products;
//only for test
public enum ProductPermissions {
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
