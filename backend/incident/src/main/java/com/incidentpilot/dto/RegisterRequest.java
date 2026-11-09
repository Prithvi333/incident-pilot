package com.incidentpilot.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Username may only contain letters, digits, dots, hyphens, and underscores")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
    private String password;

    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    /** Create a new organization with this name; the registering user becomes its ADMIN. */
    @Size(min = 2, max = 100, message = "Organization name must be 2-100 characters")
    private String organizationName;

    /** Join an existing organization (as ENGINEER) using its invite code. Exclusive with organizationName. */
    @Size(max = 64, message = "Invite code must not exceed 64 characters")
    private String inviteCode;
}
