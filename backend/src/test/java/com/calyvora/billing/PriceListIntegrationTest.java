package com.calyvora.billing;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prices live in the database so the platform owner can change them without a deploy.
 *
 * <p>The test this class exists for is {@link #changing_the_price_does_not_rewrite_old_invoices()}.
 * A price list that applied retroactively would silently restate what customers were already
 * invoiced — the billing page would stop being something anyone could check, and a dispute would be
 * unanswerable.
 */
class PriceListIntegrationTest extends IntegrationTestBase {

    private static final String PW = "demopass123";

    @Autowired
    private PricingService pricingService;

    @Test
    void the_seeded_list_is_the_published_one() {
        assertThat(pricingService.monthlyFor(null, 100, YearMonth.now())).isEqualByComparingTo("14900");
        assertThat(pricingService.monthlyFor(null, 101, YearMonth.now())).isEqualByComparingTo("14999");
    }

    @Test
    void a_new_list_applies_from_its_start_date() {
        YearMonth thisMonth = YearMonth.now();
        pricingService.publish(thisMonth.atDay(1), "cheaper",
                List.of(new PricingService.TierInput(null, new BigDecimal("50"))));

        assertThat(pricingService.monthlyFor(null, 10, thisMonth)).isEqualByComparingTo("500");
    }

    @Test
    void changing_the_price_does_not_rewrite_old_invoices() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        YearMonth thisMonth = YearMonth.now();
        // Last month was billed on the original list and must stay that way.
        BigDecimal billedThen = pricingService.monthlyFor(null, 10, lastMonth);

        pricingService.publish(thisMonth.atDay(1), "price rise",
                List.of(new PricingService.TierInput(null, new BigDecimal("500"))));

        assertThat(pricingService.monthlyFor(null, 10, lastMonth))
                .as("an invoice already issued must not change when prices do")
                .isEqualByComparingTo(billedThen);
        assertThat(pricingService.monthlyFor(null, 10, thisMonth)).isEqualByComparingTo("5000");
    }

    @Test
    void the_owner_can_read_and_publish_the_price_list() throws Exception {
        seed();
        Session owner = login("owner@priorityhr.app", PW);

        JsonNode lists = getJson("/api/v1/platform/pricing", owner);
        assertThat(lists).isNotEmpty();
        assertThat(lists.get(0).get("tiers")).isNotEmpty();

        mockMvc.perform(post("/api/v1/platform/pricing")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "effectiveFrom", LocalDate.now().plusDays(1).toString(),
                                "note", "New year pricing",
                                "tiers", List.of(
                                        Map.of("toEmployee", 50, "rate", 199),
                                        Map.of("toEmployee", 200, "rate", 149),
                                        newTier(99))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tiers.length()").value(3));
    }

    @Test
    void a_company_admin_cannot_change_the_price_list() throws Exception {
        // Pricing is the vendor's, not a customer's.
        seed();
        Session ava = login("ava.chen@northwind.demo", PW);

        mockMvc.perform(get("/api/v1/platform/pricing")
                        .header("Authorization", "Bearer " + ava.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void tiers_must_increase_and_end_open_ended() throws Exception {
        seed();
        Session owner = login("owner@priorityhr.app", PW);

        // Limits going backwards would leave a headcount matching two tiers.
        mockMvc.perform(post("/api/v1/platform/pricing")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("effectiveFrom", LocalDate.now().toString(),
                                "tiers", List.of(Map.of("toEmployee", 200, "rate", 149),
                                        Map.of("toEmployee", 100, "rate", 99), newTier(50))))))
                .andExpect(status().isBadRequest());

        // A closed final tier would leave large companies with no price at all.
        mockMvc.perform(post("/api/v1/platform/pricing")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("effectiveFrom", LocalDate.now().toString(),
                                "tiers", List.of(Map.of("toEmployee", 100, "rate", 149))))))
                .andExpect(status().isBadRequest());
    }

    /**
     * Tiers are graduated, so the cheaper rate applies only to the employees above a threshold.
     * Applying it to everyone would make the bill fall as a company grows — with ₹149/₹99 at 100,
     * the 101st hire would drop a ₹14,900 bill to ₹9,999. This must hold for any list the owner
     * configures, not just the one shipped.
     */
    @Test
    void the_bill_never_falls_as_a_company_grows() {
        YearMonth month = YearMonth.now();
        pricingService.publish(month.atDay(1), "three tiers", List.of(
                new PricingService.TierInput(25, new BigDecimal("199")),
                new PricingService.TierInput(100, new BigDecimal("149")),
                new PricingService.TierInput(null, new BigDecimal("99"))));

        BigDecimal previous = BigDecimal.ZERO;
        for (long headcount = 0; headcount <= 250; headcount++) {
            BigDecimal current = pricingService.monthlyFor(null, headcount, month);
            assertThat(current)
                    .as("adding an employee must never reduce the bill (at %d)", headcount)
                    .isGreaterThanOrEqualTo(previous);
            previous = current;
        }
    }

    @Test
    void a_negotiated_rate_ignores_the_price_list() {
        Subscription custom = new Subscription(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                new BigDecimal("80"), "INR", java.time.Instant.now());
        custom.setCustomPrice(true);

        assertThat(pricingService.monthlyFor(custom, 150, YearMonth.now())).isEqualByComparingTo("12000");
        assertThat(pricingService.rateFor(custom, 150, YearMonth.now())).isEqualByComparingTo("80");
    }

    /** An open-ended final tier — {@code Map.of} rejects a null value, so build it explicitly. */
    private static Map<String, Object> newTier(int rate) {
        Map<String, Object> tier = new java.util.HashMap<>();
        tier.put("toEmployee", null);
        tier.put("rate", rate);
        return tier;
    }

    private void seed() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }
}
