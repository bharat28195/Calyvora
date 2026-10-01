package com.calyvora.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceListRepository extends JpaRepository<PriceList, UUID> {

    /** The list in force on a date for one currency — the newest one that had already started. */
    Optional<PriceList> findFirstByCurrencyAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            String currency, LocalDate on);

    /** Newest first, for the history view. */
    List<PriceList> findAllByCurrencyOrderByEffectiveFromDesc(String currency);

    Optional<PriceList> findByCurrencyAndEffectiveFrom(String currency, LocalDate effectiveFrom);
}
