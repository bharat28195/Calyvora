package com.calyvora.company.dto;

import com.calyvora.identity.User;

/**
 * @param companyRoleId the custom role this member holds (PD-54), or null when they hold the built-in
 *                      their {@code role} names
 */
public record MemberResponse(String id, String email, String firstName, String lastName,
                             String role, String status, String companyRoleId) {

    public static MemberResponse of(User user) {
        return new MemberResponse(user.getId().toString(), user.getEmail(), user.getFirstName(),
                user.getLastName(), user.getRole().name(), user.getStatus().name(),
                user.getCompanyRoleId() == null ? null : user.getCompanyRoleId().toString());
    }
}
