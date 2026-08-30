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

    /** The currency that existed before there were several, and the fallback wherever none is given. */
    public static final String DEFAULT_CURRENCY = "INR";

    private final PriceListRepository priceListRepository;

    public PricingService(PriceListRepository priceListRepository) {
        this.priceListRepository = priceListRepository;
    }

    /** The currency a subscription is billed in. Absent one, rupees — the list that existed first. */
    public static String currencyOf(Subscription sub) {
        return sub == null || sub.getCurrency() == null || sub.getCurrency().isBlank()
                ? DEFAULT_CURRENCY : sub.getCurrency();
    }

    /** The list governing a given month in a given currency — the last day decides which applies. */
    @Transactional(readOnly = true)
    public PriceList listFor(YearMonth month, String currency) {
        return priceListRepository
                .findFirstByCurrencyAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        currency, month.atEndOfMonth())
                .orElseThrow(() -> new IllegalStateException(
                        "No " + currency + " price list is effective for " + month));
    }

    /** The tiers governing a given month. */
    @Transactional(readOnly = true)
    public List<PriceListTier> tiersFor(YearMonth month, String currency) {
        return listFor(month, currency).getTiers();
    }

    /** Today.s list, for the console and for pricing the current month. */
    @Transactional(readOnly = true)
    public PriceList current(String currency) {
        return priceListRepository
                .findFirstByCurrencyAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        currency, LocalDate.now())
                .orElseThrow(() -> new IllegalStateException("No " + currency + " price list is in force"));
    }

    @Transactional(readOnly = true)
    public List<PriceList> history(String currency) {
        return priceListRepository.findAllByCurrencyOrderByEffectiveFromDesc(currency);
    }

    /**
     * What this company pays for a month at this headcount, including the monthly minimum.
     *
     * <p>The floor doesn't apply to a company on a negotiated rate — that rate <em>is</em> its terms,
     * and quietly adding a minimum on top would charge more than was agreed.
     */
    @Transactional(readOnly = true)
    public BigDecimal monthlyFor(Subscription sub, long headcount, YearMonth month) {
        if (sub != null && sub.isCustomPrice()) {
            return sub.getPricePerEmployee().multiply(BigDecimal.valueOf(headcount));
        }
        PriceList list = listFor(month, currencyOf(sub));
        BigDecimal metered = applyTiers(list.getTiers(), headcount);
        // A company with nobody in it owes nothing; the floor is for real, small customers.
        return headcount == 0 ? metered : metered.max(list.getMonthlyMinimum());
    }

    /** True when the minimum is what this company is actually paying, so the UI can say so. */
    @Transactional(readOnly = true)
    public boolean minimumApplies(Subscription sub, long headcount, YearMonth month) {
        if (sub != null && sub.isCustomPrice() || headcount == 0) {
            return false;
        }
        PriceList list = listFor(month, currencyOf(sub));
        return applyTiers(list.getTiers(), headcount).compareTo(list.getMonthlyMinimum()) < 0;
    }

    /**
     * The cost of paying a year upfront — {@code annualMonthsCharged} months rather than twelve.
     * A customer who has prepaid is markedly less likely to drift away, which is worth more than the
     * two months given up.
     */
    @Transactional(readOnly = true)
    public BigDecimal annualPrepaidFor(Subscription sub, long headcount, YearMonth month) {
        BigDecimal monthly = monthlyFor(sub, headcount, month);
        int months = sub != null && sub.isCustomPrice() ? 12 : listFor(month, currencyOf(sub)).getAnnualMonthsCharged();
        return monthly.multiply(BigDecimal.valueOf(months));
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
        List<PriceListTier> tiers = tiersFor(month, currencyOf(sub));
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
    public PriceList publish(LocalDate effectiveFrom, String note, List<TierInput> tiers,
                             BigDecimal monthlyMinimum, Integer annualMonthsCharged, String currency) {
        validate(effectiveFrom, tiers, monthlyMinimum, annualMonthsCharged);
        String cur = currency == null || currency.isBlank() ? DEFAULT_CURRENCY : currency;
        PriceList list = priceListRepository.findByCurrencyAndEffectiveFrom(cur, effectiveFrom)
                .orElseGet(() -> new PriceList(UUID.randomUUID(), effectiveFrom, note, cur));
        // Re-publishing the same start date replaces that version rather than creating a duplicate
        // no query could choose between.
        list.getTiers().clear();
        for (TierInput t : tiers) {
            list.addTier(new PriceListTier(UUID.randomUUID(), t.upTo(), t.rate()));
        }
        list.setMonthlyMinimum(monthlyMinimum == null ? BigDecimal.ZERO : monthlyMinimum);
        list.setAnnualMonthsCharged(annualMonthsCharged == null ? 12 : annualMonthsCharged);
        return priceListRepository.save(list);
    }

    /** Convenience for callers with no commercial terms to set — keeps the floor off. */
    @Transactional
    public PriceList publish(LocalDate effectiveFrom, String note, List<TierInput> tiers) {
        return publish(effectiveFrom, note, tiers, BigDecimal.ZERO, 12, DEFAULT_CURRENCY);
    }

    private void validate(LocalDate effectiveFrom, List<TierInput> tiers,
                          BigDecimal monthlyMinimum, Integer annualMonthsCharged) {
        if (effectiveFrom == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A price list needs a start date");
        }
        if (monthlyMinimum != null && monthlyMinimum.signum() < 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A monthly minimum can't be negative");
        }
        if (annualMonthsCharged != null && (annualMonthsCharged < 1 || annualMonthsCharged > 12)) {
            // Above 12 would charge more for prepaying than for paying monthly.
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Annual billing must charge between 1 and 12 months");
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
