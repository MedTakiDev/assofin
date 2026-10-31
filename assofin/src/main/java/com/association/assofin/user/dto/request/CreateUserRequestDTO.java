package com.association.assofin.user.dto.request;

import jakarta.validation.constraints.*;

public record CreateUserRequestDTO(

        @NotEmpty(message = "Firstname is mandatory")
        @NotNull(message = "Firstname is mandatory")
        String firstname,

        @NotEmpty(message = "Lastname is mandatory")
        @NotNull(message = "Lastname is mandatory")
        String lastname,

        @Email(message = "Email is not well formatted")
        @NotEmpty(message = "Email is mandatory")
        @NotNull(message = "Email is mandatory")
        String email,

        @NotEmpty(message = "Password is mandatory")
        @NotNull(message = "Password is mandatory")
        @Size(min = 8, message = "Password should be 8 characters long minimum")
        String password,

        @NotNull(message = "Role is not set")
        @Positive
        Long roleId,

        @NotNull(message = "account enabled is mandatory")
        boolean enabled


) {
}