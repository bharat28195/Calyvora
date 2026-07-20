package com.calyvora.people.dto;

import jakarta.validation.constraints.Size;

/** Self-service: a member may update only their own contact fields (not title/status/manager). */
public record UpdateMyProfileRequest(
        @Size(max = 40) String phone,
        @Size(max = 120) String workLocation
) {
}
