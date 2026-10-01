package com.calyvora.shift.dto;

import com.calyvora.shift.Shift;

/** A shift template. */
public record ShiftResponse(
        String id,
        String name,
        String startTime,
        String endTime,
        String color
) {
    public static ShiftResponse of(Shift s) {
        return new ShiftResponse(s.getId().toString(), s.getName(),
                s.getStartTime().toString(), s.getEndTime().toString(), s.getColor());
    }
}
