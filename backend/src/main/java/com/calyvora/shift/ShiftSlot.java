package com.calyvora.shift;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Who is on which shift on which day, without the entity around it. Attendance asks this for a whole
 * team's month to know each day's start time and required hours; hydrating hundreds of entities to
 * read three columns from each was most of what that cost.
 */
public record ShiftSlot(UUID employeeId, LocalDate onDate, UUID shiftId) {
}
