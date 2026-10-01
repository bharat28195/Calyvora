package com.calyvora.billing;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A price agreed with one customer, sitting alongside the published list.
 *
 * <p>The test this class exists for is {@link #publishing_a_new_list_does_not_move_an_agreed_price()}.
 * Quoting a customer their own rate is only safe if it survives the next price change — otherwise
 * every published list silently re-prices everyone who was ever negotiated with, and the first anyone
 * would know is an invoice that does not match what was signed.
 */
class AgreedPriceIntegrationTest extends IntegrationTestBase {

    private void seed() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }

    /** Any seeded company that actually has a subscription — a price needs one to attach to. */
    private String aCompanyId(Session owner) throws Exception {
        JsonNode companies = getJson("/api/v1/platform/companies", owner);
        for (JsonNode c : companies) {
            if (!"NONE".equals(c.get("subscriptionStatus").asText())) {
                return c.get("companyId").asText();
            }
        }
        throw new AssertionError("no company with a subscription was seeded");
    }

    private JsonNode companyById(Session owner, String id) throws Exception {
        for (JsonNode c : getJson("/api/v1/platform/companies", owner)) {
            if (c.get("companyId").asText().equals(id)) {
                return c;
            }
        }
        throw new AssertionError("company " + id + " vanished from the console");
    }

    /** POST the price body, allowing an explicit null — Map.of() rejects null values. */
    private void setPrice(Session owner, String companyId, Integer price) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("price", price);
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/price")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());
    }

    @Test
    void the_console_says_whether_a_rate_was_agreed_or_came_from_the_list() throws Exception {
        seed();
        Session owner = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        String companyId = aCompanyId(owner);

        // Straight off the published list until someone agrees otherwise.
        assertThat(companyById(owner, companyId).get("customPrice").asBoolean())
                .as("a company nobody has negotiated with is on the standard list")
                .isFalse();

        setPrice(owner, companyId, 75);

        JsonNode after = companyById(owner, companyId);
        assertThat(after.get("customPrice").asBoolean()).isTrue();
        assertThat(after.get("pricePerEmployee").decimalValue()).isEqualByComparingTo("75");
    }

    @Test
    void publishing_a_new_list_does_not_move_an_agreed_price() throws Exception {
        seed();
        Session owner = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        String companyId = aCompanyId(owner);

        setPrice(owner, companyId, 75);

        // A general price rise, from today, at a rate far above what was agreed.
        mockMvc.perform(post("/api/v1/platform/pricing")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "effectiveFrom", LocalDate.now().toString(),
                                "note", "across-the-board rise",
                                "tiers", List.of(Map.of("rate", 900))))))
                .andExpect(status().isCreated());

        assertThat(companyById(owner, companyId).get("pricePerEmployee").decimalValue())
                .as("a rate agreed with a customer must survive a change to the published list")
                .isEqualByComparingTo("75");
    }

    @Test
    void a_company_can_be_put_back_on_the_standard_list() throws Exception {
        // Agreeing a price must not be a one-way door: the console offers "back to the standard
        // list", and it has to actually return the company to whatever is published at the time.
        seed();
        Session owner = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        String companyId = aCompanyId(owner);

        setPrice(owner, companyId, 75);
        assertThat(companyById(owner, companyId).get("customPrice").asBoolean()).isTrue();

        setPrice(owner, companyId, null);

        JsonNode back = companyById(owner, companyId);
        assertThat(back.get("customPrice").asBoolean()).isFalse();
        assertThat(back.get("pricePerEmployee").decimalValue())
                .as("back on the published list, not stuck on the rate that was agreed")
                .isNotEqualByComparingTo("75");
    }

    @Test
    void a_company_admin_cannot_set_their_own_price() throws Exception {
        // What a customer pays is the vendor's to decide, not theirs.
        seed();
        Session owner = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        String companyId = aCompanyId(owner);
        Session ava = login("ava.chen@northwind.demo", "demopass123");

        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/price")
                        .header("Authorization", "Bearer " + ava.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("price", 1))))
                .andExpect(status().isForbidden());
    }
}
