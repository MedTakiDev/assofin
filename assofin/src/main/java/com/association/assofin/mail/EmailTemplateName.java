package com.association.assofin.mail;


import lombok.Getter;

@Getter
public enum EmailTemplateName {
    ACTIVATION_ACCOUNT("activate_account"),
    CONFIRM_EMAIL("confirm-email");

    private final String name;

    EmailTemplateName(String name) {
        this.name = name;
    }
}
