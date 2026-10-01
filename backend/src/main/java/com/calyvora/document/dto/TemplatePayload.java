package com.calyvora.document.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create/update a template. On create, {@code name} and {@code body} are required (checked in the service). */
public record TemplatePayload(
        @Size(max = 160) String name,
        @Pattern(regexp = "OFFER_LETTER|JOINING_LETTER|RELIEVING_LETTER|EXPERIENCE_LETTER|PROMOTION_LETTER|CUSTOM",
                message = "invalid kind") String kind,
        @Size(max = 400) String description,
        String body,
        /** Null leaves the current setting alone; most letters want the company letterpad. */
        Boolean useLetterhead
) {}
