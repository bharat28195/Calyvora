package com.calyvora.client;

import com.calyvora.client.dto.ClientDetailResponse;
import com.calyvora.client.dto.ClientPayload;
import com.calyvora.client.dto.ClientRequestPayload;
import com.calyvora.client.dto.ClientRequestResponse;
import com.calyvora.client.dto.ClientResponse;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Clients + their requests (feedback D1 ⭐). Tenant-scoped; auth required. */
@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    public List<ClientResponse> list() {
        return clientService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse create(@Valid @RequestBody ClientPayload payload, @CurrentUser AuthPrincipal principal) {
        return clientService.create(payload, principal);
    }

    @GetMapping("/{clientId}")
    public ClientDetailResponse detail(@PathVariable UUID clientId) {
        return clientService.detail(clientId);
    }

    @PatchMapping("/{clientId}")
    public ClientResponse update(@PathVariable UUID clientId, @Valid @RequestBody ClientPayload payload) {
        return clientService.update(clientId, payload);
    }

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clientId) {
        clientService.delete(clientId);
    }

    // ---- requests ----

    @PostMapping("/{clientId}/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientRequestResponse addRequest(@PathVariable UUID clientId, @Valid @RequestBody ClientRequestPayload payload) {
        return clientService.addRequest(clientId, payload);
    }

    @PatchMapping("/{clientId}/requests/{requestId}")
    public ClientRequestResponse updateRequest(@PathVariable UUID clientId, @PathVariable UUID requestId,
                                               @Valid @RequestBody ClientRequestPayload payload) {
        return clientService.updateRequest(requestId, payload);
    }

    @DeleteMapping("/{clientId}/requests/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRequest(@PathVariable UUID clientId, @PathVariable UUID requestId) {
        clientService.deleteRequest(requestId);
    }
}
