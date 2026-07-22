package com.calyvora.feed.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Write or edit a post. {@code departmentId} is required when visibility is DEPARTMENT. */
public record PostPayload(
        @Size(max = 4000) String body,
        @Pattern(regexp = "UPDATE|ANNOUNCEMENT|CELEBRATION|QUESTION", message = "invalid kind") String kind,
        @Pattern(regexp = "COMPANY|DEPARTMENT", message = "invalid visibility") String visibility,
        String departmentId
) {}
