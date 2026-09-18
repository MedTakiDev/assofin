package com.association.assofin.role;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
//application starting roles

@Getter
public enum RoleEnum implements GrantedAuthority {
    ROLE_USER("ROLE_USER"),
    ROLE_ADMIN("ROLE_ADMIN");

    private final String displayName;

    RoleEnum(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getAuthority() {
        return this.name();
    }
}
