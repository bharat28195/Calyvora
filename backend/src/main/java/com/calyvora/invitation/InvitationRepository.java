package com.calyvora.invitation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    Optional<Invitation> findByIdAndCompanyId(UUID id, UUID companyId);

    List<Invitation> findByCompanyIdAndStatusOrderByCreatedAtDesc(UUID companyId, InvitationStatus status);

    Optional<Invitation> findByCompanyIdAndEmailAndStatus(UUID companyId, String email, InvitationStatus status);

    long countByCompanyIdAndStatus(UUID companyId, InvitationStatus status);
}
