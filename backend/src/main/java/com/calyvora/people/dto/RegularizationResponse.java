package com.calyvora.people.dto;

import com.calyvora.people.AttendanceRegularization;

import java.util.Map;
import java.util.UUID;

/** A regularization request with the employee's display name resolved. */
public record RegularizationResponse(
        String id,
        String employeeId,
        String employeeName,
        String date,
        String checkIn,
        String checkOut,
        String status,
        String reason,
        String decisionNote,
        String decidedAt,
        String createdAt
) {
    public static RegularizationResponse of(AttendanceRegularization r, Map<UUID, String> names) {
        return new RegularizationResponse(
                r.getId().toString(), r.getEmployeeId().toString(),
                names.getOrDefault(r.getEmployeeId(), "Employee"),
                r.getOnDate().toString(),
                r.getCheckIn() == null ? null : r.getCheckIn().toString(),
                r.getCheckOut() == null ? null : r.getCheckOut().toString(),
                r.getStatus().name(), r.getReason(), r.getDecisionNote(),
                r.getDecidedAt() == null ? null : r.getDecidedAt().toString(),
                r.getCreatedAt().toString());
    }
}
