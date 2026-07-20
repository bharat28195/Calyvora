package com.calyvora.people.dto;

public record LeaveBalanceResponse(int allowanceDays, int usedDays, int remainingDays, int pendingDays) {
}
