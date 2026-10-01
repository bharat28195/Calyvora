package com.calyvora.people.dto;

import java.util.List;

/**
 * A month of attendance for a group of people, one row per calendar day.
 *
 * <p>What a month calendar needs and nothing else: the shape of each day, not who was in it. The
 * day sheet answers "who", one date at a time; asking it thirty times to colour a grid would move
 * a thousand people across the wire per month.
 */
public record AttendanceMonthSummaryResponse(
        String month,
        long headcount,
        List<Day> days
) {
    /** {@code holidayName} is set only when {@code holiday} is true. */
    public record Day(
            String date,
            long present,
            long onLeave,
            long absent,
            long unmarked,
            long weekOff,
            boolean holiday,
            String holidayName
    ) {
    }
}
