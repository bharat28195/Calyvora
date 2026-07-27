package com.calyvora.platform.dto;

/** A pending seat request as the owner sees it in the console queue. */
public record SeatRequestResponse(
        String id,
        String companyId,
        String companyName,
        int currentSeats,
        int requestedSeats,
        String status,
        String note,
        String createdAt
) {
}
