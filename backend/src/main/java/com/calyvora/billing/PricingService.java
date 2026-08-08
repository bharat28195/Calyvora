package com.calyvora.billing;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a company is charged, from the price list that was in force for the month being billed.
 *
 * <p>Two rules shape everything here. <b>Prices are data, not code</b> — the platform owner edits them
 * in the console and the change is live immediately, because needing a deploy to change your own
 * prices is a reason not to change them. And <b>a price change is never retroactive</b>: each month is
 * priced by the list effective then, so last month's invoice still reads what the customer was
 * actually asked to pay. Getting that wrong turns the billing page into something nobody can check.
 *
 * <p>A company the owner has quoted a flat rate ({@link Subscription#isCustomPrice()}) sits outside
 * the list entirely.
 */
@Service
public class PricingService {

    private final PriceListRepository priceListRepository;

    public PricingService(PriceListRepository priceListRepository) {
        this.priceListRepository = priceListRepository;
    }

    /** The tiers governing a given month — the last day is what decides which list applies. */
    @Transactional(readOnly = true)
    public List<PriceListTier> tiersFor(YearMonth month) {
        return priceListRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(month.atEndOfMonth())
                .map(PriceList::getTiers)
                .orElseThrow(() -> new IllegalStateException(
                        "No price list is effective for " + month + " — V37 seeds one from 2020-01-01"));
    }

    /** Today's list, for the console and for pricing the current month. */
    @Transactional(readOnly = true)
    public PriceList current() {
        return priceListRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate.now())
                .orElseThrow(() -> new IllegalStateException("No price list is in force"));
    }

    @Transactional(readOnly = true)
    public List<PriceList> history() {
        return priceListRepository.findAllByOrderByEffectiveFromDesc();
    }

    /** What this company pays for a month at this headcount. */
    @Transactional(readOnly = true)
    public BigDecimal monthlyFor(Subscription sub, long headcount, YearMonth month) {
        if (sub != null && sub.isCustomPrice()) {
            return sub.getPricePerEmployee().multiply(BigDecimal.valueOf(headcount));
        }
        return applyTiers(tiersFor(month), headcount);
    }

    /**
     * The rate the next employee would be charged at — what a customer means by "our price". A
     * blended average (bill ÷ headcount) appears on no price list and answers no question they have.
     */
    @Transactional(readOnly = true)
    public BigDecimal rateFor(Subscription sub, long headcount, YearMonth month) {
        if (sub != null && sub.isCustomPrice()) {
            return sub.getPricePerEmployee();
        }
        List<PriceListTier> tiers = tiersFor(month);
        for (PriceListTier tier : tiers) {
            if (tier.getUpTo() == null || headcount < tier.getUpTo()) {
                return tier.getRate();
            }
        }
        return tiers.get(tiers.size() - 1).getRate();
    }

    /**
     * Graduated: each tier's rate applies only to the employees inside that band.
     *
     * <p>Charging every employee the lowest reached rate would make the bill <em>fall</em> as a
     * company grows — with ₹149/₹99 at a threshold of 100, the 101st hire would drop a ₹14,900 bill
     * to ₹9,999. Whatever tiers the owner configures, this keeps the total rising with headcount.
     */
    private BigDecimal applyTiers(List<PriceListTier> tiers, long headcount) {
        BigDecimal total = BigDecimal.ZERO;
        long counted = 0;
        for (PriceListTier tier : tiers) {
            if (counted >= headcount) {
                break;
            }
            long ceiling = tier.getUpTo() == null ? headcount : tier.getUpTo();
            long inTier = Math.min(headcount, ceiling) - counted;
            if (inTier <= 0) {
                continue;
            }
            total = total.add(tier.getRate().multiply(BigDecimal.valueOf(inTier)));
            counted += inTier;
        }
        return total;
    }

    /**
     * Publish a new version of the price list.
     *
     * @param effectiveFrom the day it starts applying. Dating it in the past re-prices months that
     *                      have already been invoiced, so the caller must mean it.
     */
    @Transactional
    public PriceList publish(LocalDate effectiveFrom, String note, List<TierInput> tiers) {
        validate(effectiveFrom, tiers);
        PriceList list = priceListRepository.findByEffectiveFrom(effectiveFrom)
                .orElseGet(() -> new PriceList(UUID.randomUUID(), effectiveFrom, note));
        // Re-publishing the same start date replaces that version rather than creating a duplicate
        // no query could choose between.
        list.getTiers().clear();
        for (TierInput t : tiers) {
            list.addTier(new PriceListTier(UUID.randomUUID(), t.upTo(), t.rate()));
        }
        return priceListRepository.save(list);
    }

    private void validate(LocalDate effectiveFrom, List<TierInput> tiers) {
        if (effectiveFrom == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A price list needs a start date");
        }
        if (tiers == null || tiers.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A price list needs at least one tier");
        }
        List<TierInput> ordered = new ArrayList<>(tiers);
        for (int i = 0; i < ordered.size(); i++) {
            TierInput t = ordered.get(i);
            if (t.rate() == null || t.rate().signum() < 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Every tier needs a rate of zero or more");
            }
            boolean last = i == ordered.size() - 1;
            if (last && t.upTo() != null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "The last tier must be open-ended — leave its employee limit blank");
            }
            if (!last) {
                if (t.upTo() == null) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR,
                            "Only the last tier can be open-ended");
                }
                if (t.upTo() <= 0) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR,
                            "An employee limit must be greater than zero");
                }
                Integer previous = i == 0 ? null : ordered.get(i - 1).upTo();
                if (previous != null && t.upTo() <= previous) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR,
                            "Employee limits must increase — " + t.upTo() + " comes after " + previous);
                }
            }
        }
    }

    /** @param upTo employees this tier covers, cumulative; null on the final open-ended tier. */
    public record TierInput(Integer upTo, BigDecimal rate) {}
}
