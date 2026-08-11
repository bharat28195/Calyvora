package com.calyvora.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Keyed by company id, because a company has exactly one letterpad. */
public interface LetterheadRepository extends JpaRepository<Letterhead, UUID> {
}
