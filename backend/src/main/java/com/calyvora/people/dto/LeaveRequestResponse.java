package com.calyvora.people.dto;

import com.calyvora.people.LeaveRequest;

public record LeaveRequestResponse(
        String id,
        String employeeId,
        String employeeName,
        String type,
        String startDate,
        String endDate,
        int days,
        String reason,
        String status,
        String decidedAt,
        String createdAt
) {
    public static LeaveRequestResponse of(LeaveRequest r, String employeeName) {
        return new LeaveRequestResponse(
                r.getId().toString(), r.getEmployeeId().toString(), employeeName,
                r.getType().name(), r.getStartDate().toString(), r.getEndDate().toString(),
                r.getDays(), r.getReason(), r.getStatus().name(),
                r.getDecidedAt() == null ? null : r.getDecidedAt().toString(),
                r.getCreatedAt().toString());
    }
}
