package com.aninditb.shortlink.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        // max 72: BCrypt silently truncates longer inputs, so cap here rather than let that happen invisibly
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
