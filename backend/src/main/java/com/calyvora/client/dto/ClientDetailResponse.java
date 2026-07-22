package com.calyvora.client.dto;

import com.calyvora.client.Client;

import java.util.List;

/** A client with the full list of what they've requested (feedback D1). */
public record ClientDetailResponse(
        ClientResponse client,
        List<ClientRequestResponse> requests
) {
    public static ClientDetailResponse of(Client c, List<ClientRequestResponse> requests) {
        long open = requests.stream().filter(r -> !"DELIVERED".equals(r.status()) && !"DECLINED".equals(r.status())).count();
        return new ClientDetailResponse(ClientResponse.of(c, open), requests);
    }
}
