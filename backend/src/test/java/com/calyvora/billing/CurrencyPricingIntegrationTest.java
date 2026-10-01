package com.calyvora.billing;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One published price list per currency (V44).
 *
 * <p>The test this class exists for is {@link #the_two_lists_are_priced_independently()}. The USD list
 * is <em>not</em> a converted rupee list: ₹149 is about $1.70, roughly six times under the cheapest
 * credible competitor in that market, and a price that far below the field reads as "not serious"
 * rather than as a bargain. So the two must be able to move without dragging each other, and a change
 * published to one must be invisible to the other.
 */
class CurrencyPricingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private PricingService pricingService;

    private Subscription subscriptionIn(String currency) {
        return new Subscription(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                new BigDecimal("100"), currency, null);
    }

    @Test
    void the_seeded_usd_list_is_priced_for_its_own_market() {
        // $6 up to 100, $5 above — against BambooHR's ~$10 and Gusto's $6 plus a $49 base.
        YearMonth now = YearMonth.now();
        assertThat(pricingService.rateFor(subscriptionIn("USD"), 20, now)).isEqualByComparingTo("6");
        assertThat(pricingService.rateFor(subscriptionIn("USD"), 150, now)).isEqualByComparingTo("5");

        // 20 employees × $6 = $120, comfortably over the $49 floor.
        assertThat(pricingService.monthlyFor(subscriptionIn("USD"), 20, now)).isEqualByComparingTo("120");
    }

    @Test
    void the_usd_minimum_protects_the_smallest_accounts() {
        // 3 × $6 = $18, so the $49 floor is what is actually charged.
        assertThat(pricingService.monthlyFor(subscriptionIn("USD"), 3, YearMonth.now()))
                .isEqualByComparingTo("49");
    }

    @Test
    void a_rupee_customer_is_unaffected_by_the_usd_list_existing() {
        assertThat(pricingService.rateFor(subscriptionIn("INR"), 20, YearMonth.now()))
                .isEqualByComparingTo("149");
    }

    @Test
    void a_subscription_with_no_currency_is_still_billed_in_rupees() {
        // Every company created before there was a choice. Falling through to "no list found" would
        // turn an ordinary bill into an exception.
        assertThat(pricingService.rateFor(null, 20, YearMonth.now())).isEqualByComparingTo("149");
    }

    @Test
    void the_two_lists_are_priced_independently() {
        YearMonth month = YearMonth.now();
        BigDecimal rupeesBefore = pricingService.rateFor(subscriptionIn("INR"), 10, month);

        pricingService.publish(month.atDay(1), "US price rise",
                List.of(new PricingService.TierInput(null, new BigDecimal("9"))),
                new BigDecimal("99"), 10, "USD");

        assertThat(pricingService.rateFor(subscriptionIn("USD"), 10, month)).isEqualByComparingTo("9");
        assertThat(pricingService.rateFor(subscriptionIn("INR"), 10, month))
                .as("publishing a USD list must not move what an Indian customer pays")
                .isEqualByComparingTo(rupeesBefore);
    }

    @Test
    void the_console_reads_and_publishes_one_currency_at_a_time() throws Exception {
        Session owner = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);

        JsonNode usd = getJson("/api/v1/platform/pricing?currency=USD", owner);
        assertThat(usd).isNotEmpty();
        assertThat(usd.get(0).get("currency").asText()).isEqualTo("USD");

        JsonNode inr = getJson("/api/v1/platform/pricing?currency=INR", owner);
        assertThat(inr.get(0).get("currency").asText()).isEqualTo("INR");

        mockMvc.perform(post("/api/v1/platform/pricing")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "effectiveFrom", java.time.LocalDate.now().plusDays(1).toString(),
                                "note", "USD update",
                                "currency", "USD",
                                "monthlyMinimum", 59,
                                "annualMonthsCharged", 10,
                                "tiers", List.of(Map.of("rate", 7))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void a_company_can_be_created_billed_in_dollars() throws Exception {
        // The gap this closes: the console used to hardcode INR on every company it provisioned, so a
        // customer outside India could not be onboarded on the standard list at all.
        Session owner = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);

        mockMvc.perform(post("/api/v1/platform/companies")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "companyName", "Stateside Inc",
                                "adminFirstName", "Sam", "adminLastName", "Reed",
                                "adminEmail", "sam@stateside.test",
                                "password", "demopass123",
                                "seats", 10, "months", 12,
                                "currency", "USD"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("USD"));

        // And it is billed from the USD list, not a rupee figure relabelled with a dollar sign.
        JsonNode companies = getJson("/api/v1/platform/companies", owner);
        JsonNode created = null;
        for (JsonNode c : companies) {
            if ("Stateside Inc".equals(c.get("name").asText())) {
                created = c;
            }
        }
        assertThat(created).isNotNull();
        assertThat(created.get("currency").asText()).isEqualTo("USD");
    }

    @Test
    void a_company_created_without_a_currency_is_still_in_rupees() throws Exception {
        Session owner = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);

        mockMvc.perform(post("/api/v1/platform/companies")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "companyName", "Homeside Ltd",
                                "adminFirstName", "Asha", "adminLastName", "Rao",
                                "adminEmail", "asha@homeside.test",
                                "password", "demopass123",
                                "seats", 10, "months", 12))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void a_company_admin_cannot_read_the_price_lists() throws Exception {
        mockMvc.perform(get("/api/v1/platform/pricing?currency=USD"))
                .andExpect(status().isUnauthorized());
    }
}
