package com.calyvora.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Add or edit a candidate on a job. */
public record CandidatePayload(
        @NotBlank @Size(max = 140) String name,
        @Size(max = 200) String email,
        @Size(max = 40) String phone,
        @Size(max = 500) String resumeUrl,
        @Size(max = 60) String source,
        String stage,
        Integer rating,
        String notes
) {
}
