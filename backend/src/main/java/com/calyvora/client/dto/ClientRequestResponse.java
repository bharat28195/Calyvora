package com.calyvora.client.dto;

import com.calyvora.client.ClientRequest;

/** A client request/ask (feedback D1). */
public record ClientRequestResponse(
        String id,
        String title,
        String description,
        String status,
        String createdAt
) {
    public static ClientRequestResponse of(ClientRequest r) {
        return new ClientRequestResponse(r.getId().toString(), r.getTitle(), r.getDescription(),
                r.getStatus().name(), r.getCreatedAt().toString());
    }
}
