package com.calyvora.people.dto;

import java.util.List;

/** The whole team's day sheet, plus the headline counts (feedback C.4 / B3). */
public record AttendanceDayResponse(
        String date,
        long headcount,
        long present,
        long onLeave,
        long absent,
        long unmarked,
        List<AttendanceEntryResponse> entries
) {}
