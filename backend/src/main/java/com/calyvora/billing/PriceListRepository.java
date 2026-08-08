package com.calyvora.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceListRepository extends JpaRepository<PriceList, UUID> {

    /** The list in force on a date — the newest one that had already started. */
    Optional<PriceList> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate on);

    /** Newest first, for the history view. */
    List<PriceList> findAllByOrderByEffectiveFromDesc();

    Optional<PriceList> findByEffectiveFrom(LocalDate effectiveFrom);
}
