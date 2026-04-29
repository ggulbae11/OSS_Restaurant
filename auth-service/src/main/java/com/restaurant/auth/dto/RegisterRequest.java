package com.restaurant.auth.dto;

import com.restaurant.auth.domain.User.Role;
import jakarta.validation.constraints.*;

// ── 회원가입 요청
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 30) String username,
        @NotBlank @Size(min = 8)           String password,
        Role role
) {}
