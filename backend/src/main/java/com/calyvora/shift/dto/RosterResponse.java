package com.calyvora.shift.dto;

import java.util.List;

/**
 * A week's roster grid: the 7 dates of the week, the available shift templates, the employees being
 * scheduled, and the flat list of who-works-what-when. The frontend pivots {@code assignments} into a
 * grid (employee rows × day columns).
 */
public record RosterResponse(
        String weekStart,
        List<String> days,
        List<ShiftResponse> shifts,
        List<RosterEmployee> employees,
        List<RosterEntry> assignments
) {
    /** An employee row in the roster grid. */
    public record RosterEmployee(String employeeId, String name, String jobTitle) {
    }

    /** One cell: employee {@code employeeId} works shift {@code shiftId} on {@code onDate}. */
    public record RosterEntry(String id, String employeeId, String shiftId, String onDate) {
    }
}
