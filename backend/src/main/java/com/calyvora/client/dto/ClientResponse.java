package com.calyvora.client.dto;

import com.calyvora.client.Client;

/** A client list entry with a rollup of open requests. */
public record ClientResponse(
        String id,
        String name,
        String contactName,
        String contactEmail,
        String phone,
        String website,
        String status,
        String notes,
        String createdAt,
        long openRequests
) {
    public static ClientResponse of(Client c, long openRequests) {
        return new ClientResponse(c.getId().toString(), c.getName(), c.getContactName(), c.getContactEmail(),
                c.getPhone(), c.getWebsite(), c.getStatus().name(), c.getNotes(),
                c.getCreatedAt().toString(), openRequests);
    }
}
