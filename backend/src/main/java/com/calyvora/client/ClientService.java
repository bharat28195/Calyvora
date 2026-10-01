package com.calyvora.client;

import com.calyvora.client.dto.ClientDetailResponse;
import com.calyvora.client.dto.ClientPayload;
import com.calyvora.client.dto.ClientRequestPayload;
import com.calyvora.client.dto.ClientRequestResponse;
import com.calyvora.client.dto.ClientResponse;
import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Clients and what they've requested (Clients module — feedback D1 ⭐). Tenant-scoped; any authenticated
 * member may view and manage clients (sales/support share the CRM surface).
 */
@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientRequestRepository requestRepository;

    public ClientService(ClientRepository clientRepository, ClientRequestRepository requestRepository) {
        this.clientRepository = clientRepository;
        this.requestRepository = requestRepository;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> list() {
        UUID companyId = TenantContext.getCompanyId();
        return clientRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(c -> ClientResponse.of(c, countOpen(c.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public ClientDetailResponse detail(UUID clientId) {
        Client client = require(clientId);
        List<ClientRequestResponse> requests = requestRepository
                .findByClientIdOrderByCreatedAtDesc(clientId).stream().map(ClientRequestResponse::of).toList();
        return ClientDetailResponse.of(client, requests);
    }

    @Transactional
    public ClientResponse create(ClientPayload p, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        if (p.name() == null || p.name().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Client name is required");
        }
        Client c = new Client(UUID.randomUUID(), companyId, p.name().trim(), principal.userId());
        apply(c, p);
        clientRepository.save(c);
        return ClientResponse.of(c, 0);
    }

    @Transactional
    public ClientResponse update(UUID clientId, ClientPayload p) {
        Client c = require(clientId);
        if (p.name() != null && !p.name().isBlank()) c.setName(p.name().trim());
        apply(c, p);
        return ClientResponse.of(c, countOpen(clientId));
    }

    @Transactional
    public void delete(UUID clientId) {
        Client c = require(clientId);
        // requests cascade-delete at the DB (on delete cascade); clear the parent.
        requestRepository.findByClientIdOrderByCreatedAtDesc(clientId).forEach(requestRepository::delete);
        clientRepository.delete(c);
    }

    // ---- requests ----

    @Transactional
    public ClientRequestResponse addRequest(UUID clientId, ClientRequestPayload p) {
        UUID companyId = TenantContext.getCompanyId();
        require(clientId);
        if (p.title() == null || p.title().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Request title is required");
        }
        ClientRequest r = new ClientRequest(UUID.randomUUID(), companyId, clientId, p.title().trim(),
                blankToNull(p.description()));
        if (p.status() != null) r.setStatus(RequestStatus.valueOf(p.status()));
        requestRepository.save(r);
        return ClientRequestResponse.of(r);
    }

    @Transactional
    public ClientRequestResponse updateRequest(UUID requestId, ClientRequestPayload p) {
        UUID companyId = TenantContext.getCompanyId();
        ClientRequest r = requestRepository.findByIdAndCompanyId(requestId, companyId)
                .orElseThrow(() -> new NotFoundException("Request not found"));
        if (p.title() != null && !p.title().isBlank()) r.setTitle(p.title().trim());
        if (p.description() != null) r.setDescription(blankToNull(p.description()));
        if (p.status() != null) r.setStatus(RequestStatus.valueOf(p.status()));
        return ClientRequestResponse.of(r);
    }

    @Transactional
    public void deleteRequest(UUID requestId) {
        UUID companyId = TenantContext.getCompanyId();
        ClientRequest r = requestRepository.findByIdAndCompanyId(requestId, companyId)
                .orElseThrow(() -> new NotFoundException("Request not found"));
        requestRepository.delete(r);
    }

    // ---- helpers ----

    private Client require(UUID clientId) {
        return clientRepository.findByIdAndCompanyId(clientId, TenantContext.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Client not found"));
    }

    private long countOpen(UUID clientId) {
        return requestRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .filter(r -> r.getStatus() != RequestStatus.DELIVERED && r.getStatus() != RequestStatus.DECLINED)
                .count();
    }

    private void apply(Client c, ClientPayload p) {
        if (p.contactName() != null) c.setContactName(blankToNull(p.contactName()));
        if (p.contactEmail() != null) c.setContactEmail(blankToNull(p.contactEmail()));
        if (p.phone() != null) c.setPhone(blankToNull(p.phone()));
        if (p.website() != null) c.setWebsite(blankToNull(p.website()));
        if (p.notes() != null) c.setNotes(blankToNull(p.notes()));
        if (p.status() != null) c.setStatus(ClientStatus.valueOf(p.status()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
