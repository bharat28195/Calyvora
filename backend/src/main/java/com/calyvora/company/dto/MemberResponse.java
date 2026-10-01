package com.calyvora.company.dto;

import com.calyvora.identity.User;

public record MemberResponse(String id, String email, String firstName, String lastName,
                             String role, String status) {

    public static MemberResponse of(User user) {
        return new MemberResponse(user.getId().toString(), user.getEmail(), user.getFirstName(),
                user.getLastName(), user.getRole().name(), user.getStatus().name());
    }
}
