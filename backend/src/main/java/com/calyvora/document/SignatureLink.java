package com.calyvora.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The public link a candidate signs through (PD-72, V80): the hash of a random token, pointing at a
 * company and a request. Outside row-level security, because it is read before any tenant is known.
 */
@Entity
@Table(name = "signature_links")
public class SignatureLink {

    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;
    @Column(name = "company_id", nullable = false)
    private UUID companyId;
    @Column(name = "request_id", nullable = false)
    private UUID requestId;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected SignatureLink() {
    }

    public SignatureLink(String tokenHash, UUID companyId, UUID requestId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.companyId = companyId;
        this.requestId = requestId;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() { return tokenHash; }
    public UUID getCompanyId() { return companyId; }
    public UUID getRequestId() { return requestId; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
