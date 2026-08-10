package com.calyvora.platform;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The platform owner (vendor) console: provisioning, subscription control, seat requests, and RBAC. */
class PlatformIntegrationTest extends IntegrationTestBase {

    private static final String PW = "demopass123";

    @Test
    void owner_provisions_a_company_ends_it_and_the_company_sees_the_lock() throws Exception {
        seedDemo();
        Session owner = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);

        // The seed's Northwind shows up in the console (excluding the owner's own platform company).
        JsonNode companies = getJson("/api/v1/platform/companies", owner);
        assertThat(companies.size()).isGreaterThanOrEqualTo(1);

        // Provision a new customer + its first admin.
        JsonNode created = postJson(owner, "/api/v1/platform/companies", Map.of(
                "companyName", "Testco", "adminFirstName", "Tara", "adminLastName", "Admin",
                "adminEmail", "admin@testco.demo", "password", PW, "seats", 5, "months", 6));
        String companyId = created.get("companyId").asText();
        assertThat(created.get("subscriptionStatus").asText()).isEqualTo("ACTIVE");

        // The new admin can sign in and read its (healthy) subscription.
        Session admin = login("admin@testco.demo", PW);
        assertThat(getJson("/api/v1/subscription/me", admin).get("locked").asBoolean()).isFalse();

        // Owner ends it → the company's app is locked.
        postJson(owner, "/api/v1/platform/companies/" + companyId + "/end", Map.of());
        assertThat(getJson("/api/v1/subscription/me", admin).get("locked").asBoolean()).isTrue();
    }

    @Test
    void seat_request_flows_from_admin_to_owner_and_bumps_the_limit() throws Exception {
        seedDemo();
        Session owner = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        postJson(owner, "/api/v1/platform/companies", Map.of(
                "companyName", "Seatco", "adminFirstName", "Sam", "adminLastName", "Admin",
                "adminEmail", "admin@seatco.demo", "password", PW, "seats", 5, "months", 6));

        Session admin = login("admin@seatco.demo", PW);
        postJson(admin, "/api/v1/subscription/request-seats", Map.of("seats", 12, "note", "growing"));

        JsonNode requests = getJson("/api/v1/platform/seat-requests", owner);
        assertThat(requests.size()).isEqualTo(1);
        String reqId = requests.get(0).get("id").asText();

        postJson(owner, "/api/v1/platform/seat-requests/" + reqId + "/approve", Map.of());
        assertThat(getJson("/api/v1/subscription/me", admin).get("seats").asInt()).isEqualTo(12);
    }

    @Test
    void the_console_is_owner_only() throws Exception {
        seedDemo();
        Session ava = login("ava.chen@northwind.demo", PW);   // a company ADMIN
        mockMvc.perform(get("/api/v1/platform/companies").header("Authorization", "Bearer " + ava.accessToken()))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private void seedDemo() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }

    private JsonNode postJson(Session s, String path, Map<String, ?> body) throws Exception {
        MvcResult res = mockMvc.perform(post(path).header("Authorization", "Bearer " + s.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().is2xxSuccessful()).andReturn();
        String content = res.getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }
}
