package com.calyvora.billing;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PD-79: putting a company on a plan sets what it pays.
 *
 * <p>Before this, the ₹49 Attendance & Leave plan switched modules off and left the bill on the ₹149
 * list with its ₹1,299 minimum — a shop of ten would have been sold ₹490 and billed ₹1,299.
 */
class PlanSetsPriceIntegrationTest extends IntegrationTestBase {

    private Session seedAndLogin() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        return login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
    }

    /** A seeded rupee company with a live subscription and people in it. */
    private String aCompanyId(Session owner) throws Exception {
        for (JsonNode c : getJson("/api/v1/platform/companies", owner)) {
            if (!"NONE".equals(c.get("subscriptionStatus").asText()) && !c.get("locked").asBoolean()
                    && "INR".equals(c.get("currency").asText()) && c.get("headcount").asLong() > 0) {
                return c.get("companyId").asText();
            }
        }
        throw new AssertionError("no live rupee company with people was seeded");
    }

    private JsonNode company(Session owner, String id) throws Exception {
        for (JsonNode c : getJson("/api/v1/platform/companies", owner)) {
            if (c.get("companyId").asText().equals(id)) {
                return c;
            }
        }
        throw new AssertionError("company " + id + " vanished from the console");
    }

    private void plan(Session owner, String companyId, String planCode) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("planCode", planCode);
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/plan")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());
    }

    private void price(Session owner, String companyId, Integer price) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("price", price);
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/price")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());
    }

    private static String planCode(JsonNode c) {
        JsonNode p = c.get("pricePlanCode");
        return p == null || p.isNull() ? null : p.asText();
    }

    @Test
    void choosing_a_plan_bills_the_plan_price_with_no_minimum() throws Exception {
        Session owner = seedAndLogin();
        String id = aCompanyId(owner);

        plan(owner, id, "ATTENDANCE");

        JsonNode c = company(owner, id);
        assertThat(c.get("pricePerEmployee").decimalValue()).isEqualByComparingTo("49");
        assertThat(c.get("customPrice").asBoolean()).isTrue();
        assertThat(planCode(c)).isEqualTo("ATTENDANCE");
        // ₹49 × headcount — the ₹1,299 list minimum must not sit on top of a ₹49 plan.
        BigDecimal expected = new BigDecimal("49").multiply(BigDecimal.valueOf(c.get("headcount").asLong()));
        assertThat(c.get("monthlyRevenue").decimalValue()).isEqualByComparingTo(expected);
    }

    @Test
    void moving_between_plans_moves_the_price() throws Exception {
        Session owner = seedAndLogin();
        String id = aCompanyId(owner);

        plan(owner, id, "ATTENDANCE");
        plan(owner, id, "PAYROLL");

        JsonNode c = company(owner, id);
        assertThat(c.get("pricePerEmployee").decimalValue()).isEqualByComparingTo("79");
        assertThat(planCode(c)).isEqualTo("PAYROLL");
    }

    @Test
    void a_price_agreed_by_hand_survives_a_plan_change() throws Exception {
        Session owner = seedAndLogin();
        String id = aCompanyId(owner);

        price(owner, id, 60);
        plan(owner, id, "GROWTH_2026");

        JsonNode c = company(owner, id);
        assertThat(c.get("pricePerEmployee").decimalValue())
                .as("moving a customer between plans is not a renegotiation")
                .isEqualByComparingTo("60");
        assertThat(c.get("customPrice").asBoolean()).isTrue();
        assertThat(planCode(c)).isNull();
    }

    @Test
    void typing_a_price_after_a_plan_makes_it_agreed() throws Exception {
        Session owner = seedAndLogin();
        String id = aCompanyId(owner);

        plan(owner, id, "PAYROLL");
        price(owner, id, 70);
        plan(owner, id, "COMPLETE_2026");

        JsonNode c = company(owner, id);
        assertThat(c.get("pricePerEmployee").decimalValue()).isEqualByComparingTo("70");
        assertThat(planCode(c)).isNull();
    }

    @Test
    void taking_a_company_off_its_plan_puts_a_plan_price_back_on_the_list() throws Exception {
        Session owner = seedAndLogin();
        String id = aCompanyId(owner);

        plan(owner, id, "PAYROLL");
        plan(owner, id, null);

        JsonNode c = company(owner, id);
        assertThat(c.get("customPrice").asBoolean()).isFalse();
        assertThat(planCode(c)).isNull();
    }

    @Test
    void editing_a_plan_price_later_does_not_reprice_companies_already_on_it() throws Exception {
        Session owner = seedAndLogin();
        String id = aCompanyId(owner);

        plan(owner, id, "PAYROLL");
        planPrice(owner, "PAYROLL", 89);
        try {
            assertThat(company(owner, id).get("pricePerEmployee").decimalValue())
                    .as("a price change is never retroactive")
                    .isEqualByComparingTo("79");
        } finally {
            // The catalogue is shared by every test in this context; put it back as V84 left it.
            planPrice(owner, "PAYROLL", 79);
        }
    }

    private void planPrice(Session owner, String code, int price) throws Exception {
        mockMvc.perform(patch("/api/v1/platform/plans/" + code)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pricePerEmployee", price))))
                .andExpect(status().isOk());
    }
}
