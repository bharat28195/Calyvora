package com.calyvora.people.dto;

import jakarta.validation.constraints.Size;

/** Create/update a holiday. On create, name and date are required (checked in the service). */
public record HolidayPayload(
        @Size(max = 160) String name,
        /** ISO date. */
        String date,
        Boolean optional,
        @Size(max = 400) String note
) {}
