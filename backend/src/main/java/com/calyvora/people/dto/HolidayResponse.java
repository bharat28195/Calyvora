package com.calyvora.people.dto;

import com.calyvora.people.Holiday;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** A holiday, with how far away it is so the UI doesn't have to do date maths. */
public record HolidayResponse(
        String id,
        String name,
        String date,
        boolean optional,
        String note,
        String weekday,
        long daysAway
) {
    public static HolidayResponse of(Holiday h) {
        LocalDate d = h.getDate();
        String weekday = d.getDayOfWeek().getDisplayName(
                java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
        return new HolidayResponse(h.getId().toString(), h.getName(), d.toString(), h.isOptional(),
                h.getNote(), weekday, ChronoUnit.DAYS.between(LocalDate.now(), d));
    }
}
